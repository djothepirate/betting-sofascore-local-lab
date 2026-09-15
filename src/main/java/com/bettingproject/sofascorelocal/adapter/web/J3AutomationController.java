package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3RuntimeService;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.port.J3AutomationStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.time.*;
import java.util.*;

@Controller
@RequestMapping("/j3")
public class J3AutomationController {
    private final J3RuntimeService runtime;
    private final J3AutomationStore orders;
    private final LocalFormTokenService tokens;
    public J3AutomationController(J3RuntimeService runtime,J3AutomationStore orders,LocalFormTokenService tokens) {
        this.runtime=runtime;this.orders=orders;this.tokens=tokens;
    }
    @PostMapping("/collect")
    public String collect(@RequestParam String localFormToken,@RequestParam UUID orderId,@RequestParam LocalDate date,
                          HttpSession session,RedirectAttributes flash) {
        tokens.consume(session,localFormToken);
        try {return "redirect:/j3/orders/"+runtime.manual(orderId,date,null).id();}
        catch(IllegalArgumentException | IllegalStateException failure) {return error(failure,flash,date);}
    }
    @PostMapping("/import")
    public String importPages(@RequestParam String localFormToken,@RequestParam UUID orderId,@RequestParam LocalDate date,
                              @RequestParam(required=false) List<MultipartFile> pageFiles,HttpSession session,RedirectAttributes flash) {
        tokens.consume(session,localFormToken);
        try {return "redirect:/j3/orders/"+runtime.manual(orderId,date,readPages(pageFiles)).id();}
        catch(IllegalArgumentException | IllegalStateException | com.bettingproject.sofascorelocal.application.network.J3LocalJsonImportException failure) {return error(failure,flash,date);}
    }
    static List<RawPayloadEvidence> readPages(List<MultipartFile> files) {
        if(files==null || files.isEmpty() || files.size()>35) throw new IllegalArgumentException("J3_IMPORT_PAGES_REQUIRED");
        var ordered=new TreeMap<Integer,RawPayloadEvidence>();long total=0;
        for(MultipartFile file:files) {
            if(file==null || file.isEmpty() || file.getOriginalFilename()==null
                    || !file.getOriginalFilename().matches("page-([1-9]|[12][0-9]|3[0-5])\\.json"))
                throw new IllegalArgumentException("J3_IMPORT_NAMES_INVALID");
            if(file.getSize()>RawPayloadEvidence.MAXIMUM_BYTES || (total+=file.getSize())>25L*1024*1024)
                throw new IllegalArgumentException("J3_IMPORT_SIZE_LIMIT");
            int page=Integer.parseInt(file.getOriginalFilename().substring(5,file.getOriginalFilename().length()-5));
            try {
                if(ordered.putIfAbsent(page,RawPayloadEvidence.capture(file.getBytes()))!=null)
                    throw new IllegalArgumentException("J3_IMPORT_DUPLICATE_PAGE");
            } catch(IOException unreadable) {throw new IllegalArgumentException("J3_IMPORT_READ_FAILED");}
        }
        if(ordered.firstKey()!=1 || ordered.lastKey()!=ordered.size()) throw new IllegalArgumentException("J3_IMPORT_PAGE_GAP");
        return List.copyOf(ordered.values());
    }
    @PostMapping("/settings")
    public String settings(@RequestParam String localFormToken,@RequestParam long revision,
                           @RequestParam(defaultValue="false") boolean enabled,@RequestParam Mode mode,
                           @RequestParam(required=false) LocalTime time,HttpSession session,RedirectAttributes flash) {
        tokens.consume(session,localFormToken);
        try {
            runtime.configure(revision,enabled,mode,time);
            flash.addFlashAttribute("j3Message","Préférences J3 enregistrées. Elles seront conservées après redémarrage.");
            return "redirect:/#j3-automation";
        } catch(IllegalArgumentException | IllegalStateException failure) {return error(failure,flash,null);}
    }
    @PostMapping("/plans")
    public String plan(@RequestParam String localFormToken,@RequestParam UUID ruleId,@RequestParam int revision,
                       @RequestParam(required=false) String date,@RequestParam(required=false) String at,
                       @RequestParam(required=false) String offset,HttpSession session,RedirectAttributes flash) {
        tokens.consume(session,localFormToken);
        var input=new PlanInput(ruleId,revision,date,at,offset);
        LocalDate target=null;
        try {
            target=planDate(date);
            runtime.schedule(ruleId,revision,target,planTime(at),planOffset(offset));
            flash.addFlashAttribute("j3Message","Horaire enregistré. Il sera exécuté si le laboratoire est en service et l’automatisation activée.");
            return "redirect:/#j3-automation";
        } catch(IllegalArgumentException | IllegalStateException failure) {
            return planError(failure,flash,input,target);
        } catch(InvalidDataAccessApiUsageException failure) {
            return planError(planRejection(failure),flash,input,target);
        }
    }

    /** Only this submitted form is restored, including the rule/revision of an edited plan. */
    public record PlanInput(UUID ruleId,int revision,String date,String at,String offset) { }

    private static LocalDate planDate(String value) {
        if(value==null || value.isBlank())throw new IllegalArgumentException("J3_PLAN_DATE_REQUIRED");
        try {return LocalDate.parse(value);}
        catch(DateTimeException invalid) {throw new IllegalArgumentException("J3_PLAN_DATE_INVALID");}
    }
    private static LocalDateTime planTime(String value) {
        if(value==null || value.isBlank())throw new IllegalArgumentException("J3_PLAN_TIME_REQUIRED");
        try {return LocalDateTime.parse(value);}
        catch(DateTimeException invalid) {throw new IllegalArgumentException("J3_PLAN_TIME_INVALID");}
    }
    private static ZoneOffset planOffset(String value) {
        if(value==null || value.isBlank())return null;
        try {return ZoneOffset.of(value);}
        catch(DateTimeException invalid) {throw new IllegalArgumentException("J3_TIME_OFFSET_INVALID");}
    }
    private static RuntimeException planRejection(InvalidDataAccessApiUsageException failure) {
        // Spring's JPA translator also wraps argument/state rejections from the JDBC repository.
        // Recognize only the plan's business codes; unrelated persistence failures must propagate.
        Throwable cause=failure.getCause();
        if(cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
            switch(String.valueOf(cause.getMessage())) {
                case "J3_PLAN_MUST_BE_FUTURE","J3_PLAN_IDENTITY_CONFLICT","J3_PLAN_REVISION_CONFLICT",
                     "J3_PLAN_ALREADY_ADMITTED","J3_PLAN_LIMIT" -> {return (RuntimeException)cause;}
                default -> { }
            }
        }
        throw failure;
    }
    private static String planError(RuntimeException failure,RedirectAttributes flash,PlanInput input,LocalDate date) {
        flash.addFlashAttribute("j3AutomationError",J3Presentation.reason(failure.getMessage()));
        flash.addFlashAttribute("j3PlanInput",input);
        return "redirect:/"+(date==null?"":"?j3Date="+date)+"#j3-automation";
    }
    @PostMapping("/plans/{id}/cancel")
    public String cancel(@PathVariable UUID id,@RequestParam String localFormToken,HttpSession session,RedirectAttributes flash) {
        tokens.consume(session,localFormToken);
        try {runtime.cancel(id);return "redirect:/#j3-automation";}
        catch(IllegalArgumentException | IllegalStateException failure) {return error(failure,flash,null);}
    }
    @PostMapping("/cleanup")
    public String cleanup(@RequestParam String localFormToken,HttpSession session,RedirectAttributes flash) {
        tokens.consume(session,localFormToken);runtime.retryCleanup();
        flash.addFlashAttribute("j3Message","Vérification du nettoyage demandée au propriétaire J3.");
        return "redirect:/#manual-call-control";
    }
    @GetMapping("/orders/{id}")
    public String order(@PathVariable UUID id,Model model,RedirectAttributes flash) {
        var order=orders.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(order.terminal()) {
            flash.addFlashAttribute("j3Message",order.state()==OrderState.COMPLETED?
                    "Collecte J3 réussie pour le "+order.date()+". Le catalogue est enregistré.":
                    "La collecte J3 est terminée : "+J3Presentation.state(order.state())+". "+J3Presentation.reason(order.reason()));
            return "redirect:/?j3Date="+order.date()+"#manual-call-control";
        }
        model.addAttribute("order",order);model.addAttribute("orderState",J3Presentation.state(order.state()));
        return "j3-order";
    }
    private static String error(RuntimeException failure,RedirectAttributes flash,LocalDate date) {
        flash.addFlashAttribute("j3Message",J3Presentation.reason(failure.getMessage()));
        return "redirect:/"+(date==null?"":"?j3Date="+date)+"#manual-call-control";
    }
}
