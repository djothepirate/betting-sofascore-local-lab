package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Historical evidence remains readable. WO-060 retires every old J3 mutation route. */
@Controller
public class ManualCallController {
    private final J3ManualCollectionEvidenceService evidence;
    public ManualCallController(J3ManualCollectionEvidenceService evidence) {this.evidence=evidence;}

    @PostMapping({"/manual-call/rearm","/manual-call/activate","/manual-call/prepare","/manual-call/confirm",
            "/manual-call/execute","/manual-call/import-json-pages","/manual-call/stop"})
    public ResponseEntity<String> retired() {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_PLAIN)
                .body("Le parcours J3 a été remplacé. Ouvrez le tableau de bord pour choisir une date et lancer une collecte ou un import.");
    }

    @GetMapping(value="/manual-call/evidence",produces="text/plain;charset=UTF-8")
    public ResponseEntity<String> downloadEvidence() {
        return evidence.latestDocument().map(document->ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"j3-historical-evidence.txt\"")
                .body(document.reportText())).orElseGet(()->ResponseEntity.notFound().build());
    }
}
