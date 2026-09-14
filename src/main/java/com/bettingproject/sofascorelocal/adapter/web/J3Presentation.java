package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.OrderState;

/** User-facing explanations never include exception messages, SQL, payloads or stack traces. */
public final class J3Presentation {
    private J3Presentation() { }
    public static String state(OrderState state) {
        return switch(state) {
            case FUTURE -> "Planifiée";case QUEUED -> "En attente";case RUNNING -> "En cours";
            case COMPLETED -> "Réussie";case FAILED -> "Échec";case CANCELLED -> "Annulée";
            case INTERRUPTED -> "Interrompue";case MISSED -> "Horaire manqué";case SKIPPED -> "Déjà satisfaite";
        };
    }
    public static String reason(String code) {
        if(code==null || "NONE".equals(code)) return "";
        return switch(code) {
            case "DATE_ALREADY_SUCCESSFUL" -> "Un succès est déjà enregistré pour cette date.";
            case "EXPLICIT_OCCURRENCE_HAS_PRIORITY" -> "L’horaire explicitement programmé prend en charge cette date.";
            case "AUTOMATION_DISABLED" -> "La collecte automatique est désactivée.";
            case "LAB_NOT_IN_SERVICE" -> "Le laboratoire n’était pas en service à cet horaire. Aucun rattrapage automatique.";
            case "J3_TIME_DOES_NOT_EXIST" -> "Cette heure n’existe pas à Paris lors du passage à l’heure d’été.";
            case "J3_TIME_OFFSET_REQUIRED" -> "Cette heure existe deux fois : choisissez le décalage +02:00 ou +01:00.";
            case "J3_TIME_OFFSET_INVALID" -> "Le décalage choisi ne correspond pas à cet horaire à Paris.";
            case "J3_PLAN_DATE_REQUIRED" -> "Renseignez la date du calendrier à collecter.";
            case "J3_PLAN_DATE_INVALID" -> "La date à collecter est incorrecte. Saisissez une date valide au format jour/mois/année.";
            case "J3_PLAN_TIME_REQUIRED" -> "Renseignez la date et l’heure de déclenchement à Paris.";
            case "J3_PLAN_TIME_INVALID" -> "La date ou l’heure de déclenchement est incorrecte. Saisissez une date et une heure valides à Paris.";
            case "J3_TIME_MINUTE_REQUIRED" -> "Saisissez l’heure de déclenchement en heures et minutes, sans secondes.";
            case "J3_PLAN_MUST_BE_FUTURE" -> "La date et l’heure de déclenchement sont déjà passées. Choisissez un horaire futur à Paris.";
            case "J3_SETTINGS_CHANGED","J3_PLAN_REVISION_CONFLICT","J3_PLAN_IDENTITY_CONFLICT" -> "Les paramètres ont changé. Rechargez la page avant de les modifier.";
            case "J3_PLAN_ALREADY_ADMITTED" -> "Cet ordre est déjà pris en charge et son horaire ne peut plus être modifié.";
            case "J3_IMPORT_QUEUE_FULL","J3_PLAN_LIMIT","J3_MANUAL_QUEUE_FULL" -> "La file J3 est pleine. Attendez la fin d’une collecte.";
            case "J3_LIVE_POLICY_UNSUPPORTED" -> "La campagne live active ne dispose pas de la capacité de pause J3.";
            case "PROVIDER_SUSPENDED" -> "L’accès fournisseur est suspendu après un refus. Consultez le bloc Accès fournisseur.";
            case "PROVIDER_CLEANUP_REQUIRED","PROVIDER_CLEANUP_UNVERIFIED" -> "Le nettoyage du navigateur doit être vérifié avant un nouvel appel.";
            case "J3_IMPORT_PAGES_REQUIRED","J3_IMPORT_NAMES_INVALID","J3_IMPORT_DUPLICATE_PAGE","J3_IMPORT_PAGE_GAP" ->
                    "Sélectionnez les fichiers page-1.json à page-N.json, sans trou ni doublon, avec un maximum de 35 pages.";
            case "J3_IMPORT_SIZE_LIMIT" -> "Limite dépassée : 5 Mio par page et 25 Mio pour le lot.";
            case "J3_RUNTIME_DISABLED","J3_RUNTIME_UNAVAILABLE","J3_OTHER_PROCESS_OWNER","STARTING" ->
                    "Le service J3 n’est pas disponible dans cette instance.";
            case "ADMISSION_DEADLINE_EXPIRED","LIVE_J3_DEADLINE_EXPIRED" -> "L’échéance maximale de cette collecte est dépassée.";
            case "OWNER_PROCESS_ABSENT","APPLICATION_STOPPED","EXECUTION_INTERRUPTED" ->
                    "L’exécution a été interrompue. Les succès précédents restent consultables.";
            default -> code.startsWith("J3_IMPORT") || code.contains("PAGE") || code.contains("PARSE") || code.contains("SCHEMA") ?
                    "Le lot n’a pas été validé. Vérifiez la structure J3, l’ordre des pages et la dernière page hasNextPage=false." :
                    "La collecte n’est pas disponible ou a été arrêtée. Consultez la configuration du transport et les diagnostics locaux.";
        };
    }
}
