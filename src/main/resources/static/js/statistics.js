(() => {
  "use strict";
  const versions = new WeakMap();
  const create = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };
  const percentage = value => typeof value === "number" && Number.isFinite(value) && value >= 0 && value <= 100;

  function selectPeriod(host, code, announce) {
    const select = host.querySelector("[data-stat-select]");
    const panels = Array.from(host.querySelectorAll("[data-stat-period]"));
    panels.forEach(panel => { panel.hidden = panel.dataset.statPeriod !== code; });
    select.value = code;
    if (announce) host.querySelector("[data-stat-announcement]").textContent =
      `${select.selectedOptions[0]?.textContent || code} sélectionnée.`;
  }

  function enhance(host) {
    const select = host.querySelector("[data-stat-select]");
    if (!select || host.dataset.statEnhanced === "true") return;
    host.dataset.statEnhanced = "true";
    host.querySelector("[data-stat-control]").hidden = select.options.length === 0;
    select.addEventListener("change", () => selectPeriod(host, select.value, true));
    const preferred = Array.from(select.options).some(option => option.value === host.dataset.statDefault)
      ? host.dataset.statDefault : select.options[0]?.value || "";
    selectPeriod(host, preferred, false);
  }

  function valueNode(value, side) {
    const node = create("div", `statistics-value statistics-${side}`);
    node.dataset.statValueState = value.state;
    node.append(create("span", "statistics-side", side === "home" ? "Domicile" : "Extérieur"),
      create("strong", "", value.text));
    if (value.ratio && percentage(value.percentage)) {
      const track = create("meter", "statistics-track");
      track.setAttribute("aria-hidden", "true");
      track.min = 0;
      track.max = 100;
      track.value = value.percentage;
      node.append(track);
    }
    if (value.note) node.append(create("small", "", value.note));
    return node;
  }

  function periodNode(period, state) {
    const panel = create("details", "statistics-period");
    panel.open = state?.open ?? true;
    panel.dataset.statPeriod = period.code;
    panel.setAttribute("aria-label", period.label);
    const heading = create("summary");
    heading.append(create("h4", "", period.label));
    panel.append(heading);
    const teams = create("div", "statistics-teams");
    teams.append(create("span", "", "Domicile"), create("span", "", "Extérieur"));
    panel.append(teams);
    period.groups.forEach(group => {
      const section = create("details", "statistics-group");
      section.open = state?.groups.get(group.name) ?? true;
      section.dataset.statGroup = group.name;
      const heading = create("summary");
      heading.append(create("h5", "", group.label ?? group.name));
      section.append(heading);
      group.metrics.forEach(metric => {
        const article = create("article", "statistics-metric");
        article.append(create("h6", "", metric.label));
        const values = create("div", "statistics-values");
        values.append(valueNode(metric.home, "home"), valueNode(metric.away, "away"));
        article.append(values);
        if (percentage(metric.possessionHome)) {
          const split = create("meter", "statistics-possession");
          split.setAttribute("aria-hidden", "true");
          split.min = 0;
          split.max = 100;
          split.value = metric.possessionHome;
          article.append(split);
        }
        if (metric.note) article.append(create("p", "statistics-note", metric.note));
        section.append(article);
      });
      panel.append(section);
    });
    return panel;
  }

  function controls(host) {
    if (host.querySelector("[data-stat-select]")) return;
    host.classList.add("statistics-view");
    host.setAttribute("data-statistics", "");
    const label = create("label", "statistics-selector", "Période des statistiques ");
    label.setAttribute("data-stat-control", "");
    const select = create("select");
    select.setAttribute("data-stat-select", "");
    label.append(select);
    const announcement = create("p", "statistics-announcement");
    announcement.setAttribute("data-stat-announcement", "");
    announcement.setAttribute("role", "status");
    announcement.setAttribute("aria-live", "polite");
    const periods = create("div");
    periods.setAttribute("data-stat-periods", "");
    host.append(label, create("p", "statistics-note",
      "Seules les périodes présentes dans cette observation sont proposées. ALL n’est pas additionné aux autres périodes."),
      announcement, periods);
  }

  function update(host, view) {
    if (!view || !Array.isArray(view.periods)) return;
    controls(host);
    const version = JSON.stringify(view);
    if (versions.get(host) === version) return;
    const select = host.querySelector("[data-stat-select]");
    const previous = select.value;
    const wasEnhanced = host.dataset.statEnhanced === "true";
    const states = new Map(Array.from(host.querySelectorAll("[data-stat-period]"), panel => [
      panel.dataset.statPeriod, {
        open: panel.open,
        groups: new Map(Array.from(panel.querySelectorAll("[data-stat-group]"), group =>
          [group.dataset.statGroup, group.open]))
      }
    ]));
    const focused = document.activeElement;
    const focusedPeriod = focused?.matches("summary") && host.contains(focused)
      ? focused.closest("[data-stat-period]")?.dataset.statPeriod : undefined;
    const focusedGroup = focusedPeriod === undefined ? undefined
      : focused.closest("[data-stat-group]")?.dataset.statGroup;
    const chosen = view.periods.some(period => period.code === previous) ? previous
      : view.periods.some(period => period.code === view.defaultPeriod) ? view.defaultPeriod
      : view.periods[0]?.code || "";
    select.replaceChildren(...view.periods.map(period => {
      const option = create("option", "", period.label);
      option.value = period.code;
      return option;
    }));
    const panels = view.periods.map(period => periodNode(period, states.get(period.code)));
    host.querySelector("[data-stat-periods]").replaceChildren(...(panels.length ? panels
      : [create("p", "", "Collection normalisée vide : aucune statistique disponible.")]));
    host.dataset.statDefault = view.defaultPeriod;
    host.querySelector("[data-stat-control]").hidden = view.periods.length === 0;
    enhance(host);
    selectPeriod(host, chosen, false);
    if (focusedPeriod !== undefined) {
      const panel = panels.find(candidate => candidate.dataset.statPeriod === focusedPeriod);
      const group = focusedGroup === undefined ? null
        : Array.from(panel?.querySelectorAll("[data-stat-group]") || [])
          .find(candidate => candidate.dataset.statGroup === focusedGroup);
      const target = panel && !panel.hidden
        ? (group || panel).querySelector(":scope > summary") : select;
      target?.focus({preventScroll: true});
    }
    if (wasEnhanced && previous && previous !== chosen) {
      host.querySelector("[data-stat-announcement]").textContent =
        "La période choisie n’est plus présente dans cette observation. "
        + (select.selectedOptions[0]?.textContent || "Aucune période disponible.");
    }
    versions.set(host, version);
  }

  window.StatisticsView = {update};
  document.querySelectorAll("[data-statistics]").forEach(enhance);
})();
