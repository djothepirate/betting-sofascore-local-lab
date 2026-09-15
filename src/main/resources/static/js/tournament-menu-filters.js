"use strict";

// Only the read-only local filter form is submitted; no provider action is triggered.
const tournamentFilters = document.getElementById("tournament-menu-filters");
if (tournamentFilters) {
    tournamentFilters.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
        checkbox.addEventListener("change", () => tournamentFilters.requestSubmit());
    });
}
