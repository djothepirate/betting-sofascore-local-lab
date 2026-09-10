(() => {
  "use strict";
  const versions = new WeakMap();
  const statisticVersions = new WeakMap();
  const list = value => Array.isArray(value) ? value : [];
  const text = (value, fallback = "") => value === null || value === undefined || String(value).trim() === ""
    ? fallback : String(value);
  const create = (tag, className, marker, value) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (marker) node.setAttribute(marker, "");
    if (value !== undefined) node.textContent = value;
    return node;
  };
  const write = (node, value) => { if (node.textContent !== value) node.textContent = value; };
  const direct = (parent, selector) => Array.from(parent.children).find(node => node.matches(selector));
  const count = (value, fallback) => Number.isInteger(value) && value >= 0 ? value : fallback;
  const localFlag = /^\/images\/flags\/4x3\/(?:[a-z]{2}|gb-(?:eng|sct|wls))\.svg$/;

  function order(parent, nodes) {
    const wanted = new Set(nodes);
    nodes.forEach((node, index) => {
      const before = parent.children[index] || null;
      if (before === node) return;
      // Reinsert the existing node so layout is invalidated after a move through
      // collapsed details. Native moveBefore can retain an invisible layout box.
      // update() restores focus after reconciling the complete view.
      parent.insertBefore(node, before);
    });
    Array.from(parent.children).forEach(node => { if (!wanted.has(node)) node.remove(); });
  }

  function sectionNode(key, label) {
    const section = create("details", "lineups-section", "data-lineups-section");
    section.dataset.lineupsSection = key;
    section.open = true;
    const summary = create("summary");
    summary.append(create("span", "lineups-section-title", "", label),
      create("span", "lineups-count", "data-lineups-count", "0"));
    section.append(summary);
    return section;
  }

  function teamNode(side) {
    const team = create("details", "lineups-team", "data-lineups-team");
    team.dataset.lineupsTeam = side;
    team.open = true;
    const summary = create("summary");
    const header = create("span", "lineups-team-header");
    const title = create("span", "lineups-team-title");
    title.append(create("span", "lineups-side", "data-lineups-side-label"),
      create("strong", "", "data-lineups-team-name"));
    header.append(title, create("span", "lineups-formation", "data-lineups-formation"));
    summary.append(header);
    const body = create("div", "lineups-team-body");
    const starters = sectionNode("starters", "Titulaires");
    const field = create("div", "lineups-starters");
    field.append(create("p", "lineups-note", "", "Répartition par poste, sans positionnement tactique détaillé."),
      create("p", "lineups-empty", "data-lineups-starters-empty", "Aucun titulaire renseigné."),
      create("div", "lineups-pitch", "data-lineups-groups"));
    starters.append(field);
    const substitutes = sectionNode("substitutes", "Remplaçants");
    substitutes.append(create("ul", "lineups-players lineups-bench", "data-lineups-substitutes"),
      create("p", "lineups-empty", "data-lineups-substitutes-empty", "Aucun remplaçant renseigné."));
    const missing = sectionNode("missing", "Joueurs indisponibles");
    missing.append(create("p", "lineups-empty", "data-lineups-missing-empty"),
      create("ul", "lineups-missing-list", "data-lineups-missing-list"));
    body.append(starters, substitutes, missing);
    team.append(summary, body);
    return team;
  }

  function groupNode(key) {
    const group = create("section", "lineups-position-group", "data-lineups-group");
    group.dataset.lineupsGroup = key;
    group.append(create("h5", "lineups-position-heading", "data-lineups-group-label"),
      create("ul", "lineups-players", "data-lineups-players"));
    return group;
  }

  function playerNode(key) {
    const player = create("li", "lineups-player", "data-lineups-player");
    player.dataset.lineupsPlayer = key;
    const details = create("details", "lineups-player-details", "data-lineups-player-details");
    details.setAttribute("data-lineups-player-shell", "");
    const summary = create("summary", "lineups-player-summary", "data-lineups-player-header");
    const info = create("span", "lineups-player-info");
    const title = create("span", "lineups-player-title");
    title.append(create("strong", "lineups-name", "data-lineups-name"));
    const meta = create("span", "lineups-player-meta");
    meta.append(create("span", "", "data-lineups-position"));
    info.append(title, meta, create("span", "lineups-statistics-hint", "data-lineups-statistics-hint"));
    summary.append(create("span", "lineups-number", "data-lineups-number"), info);
    const statistics = create("div", "lineups-player-statistics", "data-lineups-player-statistics");
    const ratings = create("details", "lineups-source-details", "data-lineups-rating-versions");
    ratings.append(create("summary", "", "", "Versions de la note fournisseur"),
      create("dl", "lineups-metrics", "data-lineups-metrics"));
    statistics.append(create("p", "lineups-statistics-empty", "data-lineups-statistics-empty"),
      create("div", "", "data-lineups-statistic-groups"), ratings,
      create("p", "lineups-statistics-note", "data-lineups-statistics-note",
        "Valeurs arrondies à deux décimales pour l’affichage. Les notes conservent l’échelle du fournisseur."));
    details.append(summary, statistics);
    player.append(details);
    return player;
  }

  function updatePlayer(node, player) {
    playerInteractivity(node, player.statistics);
    const number = text(player.shirtNumber, "—");
    const badge = node.querySelector("[data-lineups-number]");
    write(badge, number);
    const numberLabel = number === "—" ? "Numéro de maillot non renseigné" : `Numéro de maillot ${number}`;
    if (badge.getAttribute("aria-label") !== numberLabel) badge.setAttribute("aria-label", numberLabel);
    write(node.querySelector("[data-lineups-name]"), text(player.name, "Joueur non renseigné"));
    write(node.querySelector("[data-lineups-position]"), text(player.positionLabel, "Poste non renseigné"));
    const captain = node.querySelector("[data-lineups-captain]");
    if (player.captain === true) {
      if (!captain) node.querySelector(".lineups-player-title")
        .append(create("span", "lineups-captain", "data-lineups-captain", "C · Capitaine"));
    } else {
      captain?.remove();
    }
    updatePlayerDecorations(node, player);
    updateStatistics(node, player.statistics);
  }

  const hasStatistics = view => list(view?.groups).some(group => list(group.metrics).length > 0)
    || list(view?.ratingVersions).length > 0;

  function playerInteractivity(node, view) {
    const interactive = hasStatistics(view);
    const old = node.querySelector("[data-lineups-player-shell], [data-lineups-player-details]");
    if (old.tagName === (interactive ? "DETAILS" : "DIV")) return;
    const replacement = playerNode(node.dataset.lineupsPlayer).firstElementChild;
    const freshHeader = replacement.firstElementChild;
    freshHeader.replaceChildren(...old.firstElementChild.childNodes);
    if (!interactive) {
      const shell = create("div", "lineups-player-static", "data-lineups-player-shell");
      const header = create("div", "lineups-player-summary", "data-lineups-player-header");
      header.tabIndex = -1;
      header.append(...freshHeader.childNodes);
      header.querySelector("[data-lineups-statistics-hint]")?.remove();
      shell.append(header);
      node.replaceChildren(shell);
    } else {
      if (!freshHeader.querySelector("[data-lineups-statistics-hint]"))
        freshHeader.querySelector(".lineups-player-info").append(
          create("span", "lineups-statistics-hint", "data-lineups-statistics-hint", "Statistiques"));
      node.replaceChildren(replacement);
    }
    statisticVersions.delete(node);
  }

  function bootIcon() {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 32 24");
    svg.setAttribute("fill", "none");
    svg.setAttribute("class", "lineups-boot");
    svg.setAttribute("data-lineups-assist-icon", "");
    for (const attributes of [
      {d: "M4 3h7l3 7 11 4c3 1 4 3 3 5H3V9l1-6Z", fill: "currentColor"},
      {d: "m12 7 5 1m-4 2 6 1M3 18h25", stroke: "white", "stroke-width": "1.5"},
      {d: "M6 19v3m7-3v3m9-3v3m5-3v3", stroke: "currentColor", "stroke-width": "2.5"}]) {
      const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
      Object.entries(attributes).forEach(([key, value]) => path.setAttribute(key, value));
      svg.append(path);
    }
    return svg;
  }

  function countryLabel() {
    const label = create("span", "country-fallback-label", "data-lineups-country-label");
    label.append(create("span", "country-accessible-prefix", "", "Pays : "),
      create("span", "", "data-lineups-country-label-text"));
    return label;
  }

  function setCountryFlagState(country, state) {
    country.dataset.countryFlagState = state;
  }

  function trackCountryFlag(country, flag, path) {
    const ready = () => {
      setCountryFlagState(country, flag.isConnected && flag.naturalWidth > 0 ? "ready" : "fallback");
    };
    flag.onload = ready;
    flag.onerror = () => setCountryFlagState(country, "fallback");
    if (flag.dataset.countryFlagPath !== path) {
      flag.dataset.countryFlagPath = path;
      setCountryFlagState(country, "pending");
      if (flag.getAttribute("src") !== path) flag.setAttribute("src", path);
    }
    if (flag.complete) ready();
  }

  function initializeCountryFlags(root = document) {
    root.querySelectorAll("[data-lineups-country]").forEach(country => {
      const flag = country.querySelector("[data-lineups-flag]");
      const path = flag?.getAttribute("src") || "";
      if (flag && localFlag.test(path)) trackCountryFlag(country, flag, path);
      else setCountryFlagState(country, "fallback");
    });
  }

  function updateCountry(container, view, before) {
    let country = container.querySelector("[data-lineups-country]");
    const label = text(view?.label);
    // The source may omit a country. Do not invent one or render a placeholder as a fact.
    const available = view?.available === true
      || (view?.available === undefined && label !== "" && label !== "Pays non renseigné");
    if (!available) {
      country?.remove();
      return;
    }
    if (!country) {
      country = create("span", "lineups-country", "data-lineups-country");
      country.append(countryLabel());
      container.insertBefore(country, before);
    }
    country.removeAttribute("aria-label");
    let labelText = country.querySelector("[data-lineups-country-label-text]");
    if (!labelText) {
      const fallback = country.querySelector("[data-lineups-country-label]") || countryLabel();
      fallback.classList.add("country-fallback-label");
      fallback.replaceChildren(create("span", "country-accessible-prefix", "", "Pays : "),
        create("span", "", "data-lineups-country-label-text"));
      if (!fallback.isConnected) country.append(fallback);
      labelText = fallback.querySelector("[data-lineups-country-label-text]");
    }
    write(labelText, label);
    const path = text(view?.flagPath);
    let flag = country.querySelector("[data-lineups-flag]");
    if (localFlag.test(path)) {
      if (!flag) {
        flag = create("img", "lineups-flag", "data-lineups-flag");
        flag.alt = ""; flag.width = 24; flag.height = 18;
        country.prepend(flag);
      }
      trackCountryFlag(country, flag, path);
    } else {
      flag?.remove();
      setCountryFlagState(country, "fallback");
    }
  }

  function updatePlayerDecorations(node, player) {
    const info = node.querySelector(".lineups-player-info");
    updateCountry(info, player.country, info.querySelector("[data-lineups-statistics-hint]"));
    let achievements = node.querySelector("[data-lineups-achievements]");
    if (!achievements) {
      achievements = create("span", "lineups-achievements", "data-lineups-achievements");
      info.insertBefore(achievements, info.querySelector("[data-lineups-statistics-hint]"));
    }
    const badges = list(player.achievements).filter(value => ["goals", "assists"].includes(value.key)
      && /^[1-9][0-9]{0,95}$/.test(text(value.count)));
    achievements.hidden = badges.length === 0;
    achievements.replaceChildren(...badges.map(value => {
      const badge = create("span", "lineups-achievement", "data-lineups-achievement");
      badge.dataset.lineupsAchievement = value.key;
      badge.setAttribute("role", "img"); badge.setAttribute("aria-label", text(value.label));
      const repetitions = /^[1-5]$/.test(value.count) ? Number(value.count) : 1;
      badge.dataset.compact = String(repetitions === 1 && value.count !== "1");
      const icons = create("span", "lineups-achievement-icons"); icons.setAttribute("aria-hidden", "true");
      for (let index = 0; index < repetitions; index++) icons.append(value.key === "goals"
        ? create("span", "", "data-lineups-goal-icon", "⚽") : bootIcon());
      const countLabel = create("span", "lineups-achievement-count", "", `×${value.count}`);
      countLabel.setAttribute("aria-hidden", "true"); badge.append(icons, countLabel);
      return badge;
    }));
  }

  function metricRows(parent, metrics) {
    const existing = new Map(Array.from(parent.children, node => [node.dataset.lineupsMetric, node]));
    order(parent, list(metrics).map(metric => {
      const node = existing.get(metric.key) || create("div", "", "data-lineups-metric");
      node.dataset.lineupsMetric = metric.key;
      if (!node.firstElementChild) node.append(create("dt"), create("dd"));
      write(node.querySelector("dt"), text(metric.label));
      write(node.querySelector("dd"), text(metric.value));
      return node;
    }));
  }

  function updateStatistics(node, view) {
    if (!hasStatistics(view)) return;
    const version = JSON.stringify(view ?? null);
    if (statisticVersions.get(node) === version) return;
    const groups = list(view?.groups), ratingVersions = list(view?.ratingVersions);
    const empty = node.querySelector("[data-lineups-statistics-empty]");
    write(node.querySelector("[data-lineups-statistics-hint]"), view ? "Statistiques" : "Détails du joueur");
    write(empty, view ? "Aucune statistique renseignée pour ce joueur." : "Statistiques non fournies pour ce joueur.");
    empty.hidden = groups.length > 0 || ratingVersions.length > 0;
    const container = node.querySelector("[data-lineups-statistic-groups]");
    const existing = new Map(Array.from(container.children, group => [group.dataset.lineupsStatisticGroup, group]));
    order(container, groups.map(group => {
      const panel = existing.get(group.key) || create("section", "lineups-statistic-group", "data-lineups-statistic-group");
      panel.dataset.lineupsStatisticGroup = group.key;
      if (!panel.firstElementChild) panel.append(create("h6", "", "data-lineups-statistic-group-label"),
        create("dl", "lineups-metrics", "data-lineups-metrics"));
      write(panel.querySelector("[data-lineups-statistic-group-label]"), text(group.label));
      metricRows(panel.querySelector("[data-lineups-metrics]"), group.metrics);
      return panel;
    }));
    const ratings = node.querySelector("[data-lineups-rating-versions]");
    ratings.hidden = ratingVersions.length === 0;
    metricRows(ratings.querySelector("[data-lineups-metrics]"), ratingVersions);
    node.querySelector("[data-lineups-statistics-note]").hidden = groups.length === 0 && ratingVersions.length === 0;
    statisticVersions.set(node, version);
  }

  function missingPlayerNode(key) {
    const player = create("li", "lineups-missing-player", "data-lineups-missing-player");
    player.dataset.lineupsMissingPlayer = key;
    player.append(create("strong", "", "data-lineups-missing-name"),
      create("p", "lineups-missing-position", "data-lineups-missing-position"),
      create("p", "", "data-lineups-missing-description"),
      create("p", "lineups-return", "data-lineups-missing-return"));
    const source = create("details", "lineups-source-details", "data-lineups-missing-source");
    const fields = create("dl", "lineups-metrics");
    for (const [key, label] of [["type", "Type"], ["reason", "Code du motif"], ["external", "Type externe"]]) {
      const field = create("div");
      field.append(create("dt", "", "", label), create("dd", "", `data-lineups-missing-${key}`));
      fields.append(field);
    }
    source.append(create("summary", "", "", "Informations fournisseur"), fields);
    player.append(source);
    return player;
  }

  function updateMissingPlayers(teamNode, values) {
    const section = teamNode.querySelector('[data-lineups-section="missing"]');
    const provided = Array.isArray(values), players = list(values);
    write(section.querySelector("[data-lineups-count]"), provided ? String(players.length) : "—");
    const empty = section.querySelector("[data-lineups-missing-empty]");
    write(empty, provided ? "Aucun joueur indisponible signalé." : "Informations sur les joueurs indisponibles non fournies.");
    empty.hidden = players.length > 0;
    const container = section.querySelector("[data-lineups-missing-list]");
    const existing = new Map(Array.from(container.children, node => [node.dataset.lineupsMissingPlayer, node]));
    const usedKeys = new Set();
    order(container, players.map((player, index) => {
      let key = text(player.key, `missing:${index}`);
      if (usedKeys.has(key)) key = `${key}|duplicate:${index}`;
      usedKeys.add(key);
      const node = existing.get(key) || missingPlayerNode(key);
      for (const [key, value] of [["name", player.name], ["description", player.description], ["type", player.type],
        ["reason", player.reason], ["external", player.externalType],
        ["position", `${player.positionLabel} · N° ${player.shirtNumber}`],
        ["return", `Retour estimé fournisseur : ${player.expectedReturn}`]])
        write(node.querySelector(`[data-lineups-missing-${key}]`), text(value));
      updateCountry(node, player.country, node.querySelector("[data-lineups-missing-description]"));
      node.querySelector("[data-lineups-missing-return]").hidden = player.expectedReturn === "—";
      return node;
    }));
  }

  function updateTeam(node, team) {
    const sideLabel = text(team.sideLabel, team.side === "AWAY" ? "Extérieur" : "Domicile");
    write(node.querySelector("[data-lineups-side-label]"), sideLabel);
    write(node.querySelector("[data-lineups-team-name]"), text(team.name, sideLabel));
    const formation = text(team.formation);
    write(node.querySelector("[data-lineups-formation]"), formation && formation !== "Formation non renseignée"
      ? `Formation · ${formation}` : "Formation non renseignée");
    const starters = node.querySelector('[data-lineups-section="starters"]');
    const substitutes = node.querySelector('[data-lineups-section="substitutes"]');
    const groups = list(team.starterGroups);
    const bench = list(team.substitutes);
    write(starters.querySelector("[data-lineups-count]"), String(count(team.starterCount,
      groups.reduce((sum, group) => sum + list(group.players).length, 0))));
    write(substitutes.querySelector("[data-lineups-count]"), String(count(team.substituteCount, bench.length)));

    // Pool across the whole team so a player moving from bench to starters keeps
    // the same node. Keys and disclosure states never leak between separate hosts.
    const players = new Map(Array.from(node.querySelectorAll("[data-lineups-player]"),
      player => [player.dataset.lineupsPlayer, player]));
    const usedKeys = new Set();
    const playerNodes = (items, scope) => list(items).map((player, index) => {
      let key = text(player.key, `${scope}:${index}`);
      if (usedKeys.has(key)) key = `${key}|duplicate:${scope}:${index}`;
      usedKeys.add(key);
      const item = players.get(key) || playerNode(key);
      updatePlayer(item, player);
      return item;
    });
    const field = node.querySelector("[data-lineups-groups]");
    const existingGroups = new Map(Array.from(field.children, group => [group.dataset.lineupsGroup, group]));
    const desiredGroups = groups.map((group, index) => {
      const key = text(group.key, `UNKNOWN:${index}`);
      const panel = existingGroups.get(key) || groupNode(key);
      write(panel.querySelector("[data-lineups-group-label]"), text(group.label, "Poste non renseigné"));
      order(panel.querySelector("[data-lineups-players]"), playerNodes(group.players, `starters:${key}`));
      return panel;
    });
    order(field, desiredGroups);
    field.hidden = desiredGroups.length === 0;
    node.querySelector("[data-lineups-starters-empty]").hidden = groups.some(group => list(group.players).length > 0);
    order(node.querySelector("[data-lineups-substitutes]"), playerNodes(bench, "substitutes"));
    node.querySelector("[data-lineups-substitutes-empty]").hidden = bench.length > 0;
    updateMissingPlayers(node, team.missingPlayers);
  }

  function controls(host) {
    host.classList.add("lineups-view");
    host.setAttribute("data-lineups", "");
    let status = direct(host, ".lineups-status");
    if (!status) {
      status = create("div", "lineups-status");
      status.append(create("span", "lineups-confirmation", "data-lineups-confirmation"));
      host.append(status);
    }
    let empty = direct(host, "[data-lineups-empty]");
    if (!empty) {
      empty = create("p", "lineups-empty", "data-lineups-empty", "Aucune composition disponible dans cette observation.");
      host.append(empty);
    }
    let teams = direct(host, "[data-lineups-teams]");
    if (!teams) {
      teams = create("div", "lineups-teams", "data-lineups-teams");
      host.append(teams);
    }
    return {confirmation: status.querySelector("[data-lineups-confirmation]"), empty, teams};
  }

  function update(host, view) {
    if (!host || !view || !Array.isArray(view.teams)) return;
    const version = JSON.stringify(view);
    if (versions.get(host) === version) return;
    const focused = host.contains(document.activeElement) ? document.activeElement : null;
    const focusTeam = focused?.closest("[data-lineups-team]")?.dataset.lineupsTeam;
    const focusSection = focused?.closest("[data-lineups-section]")?.dataset.lineupsSection;
    const focusPlayer = focused?.closest("[data-lineups-player]")?.dataset.lineupsPlayer;
    const parts = controls(host);
    write(parts.confirmation, text(view.confirmationLabel,
      view.confirmed === true ? "Compositions confirmées" : "Compositions provisoires"));
    parts.confirmation.dataset.confirmed = String(view.confirmed === true);
    const existing = new Map(Array.from(parts.teams.children, team => [team.dataset.lineupsTeam, team]));
    const teams = view.teams.map((team, index) => {
      const key = text(team.side, `TEAM:${index}`);
      const panel = existing.get(key) || teamNode(key);
      updateTeam(panel, team);
      return panel;
    });
    order(parts.teams, teams);
    parts.empty.hidden = teams.length > 0;
    if (focused) {
      const team = teams.find(panel => panel.dataset.lineupsTeam === focusTeam);
      const player = Array.from(team?.querySelectorAll("[data-lineups-player]") || [])
        .find(node => node.dataset.lineupsPlayer === focusPlayer);
      const section = player?.closest("[data-lineups-section]")
        || Array.from(team?.querySelectorAll("[data-lineups-section]") || [])
          .find(panel => panel.dataset.lineupsSection === focusSection);
      // A connected control can become hidden when rating versions disappear or
      // its player moves into a collapsed section. Confirm focus restoration
      // succeeded, then fall back through the visible containing summaries.
      for (const candidate of [focused, player?.querySelector("[data-lineups-player-header]"),
        section?.querySelector(":scope > summary"), team?.querySelector(":scope > summary")]) {
        if (!candidate?.isConnected || !host.contains(candidate) || candidate.getClientRects().length === 0
            || getComputedStyle(candidate).visibility !== "visible") continue;
        if (document.activeElement !== candidate) candidate.focus({preventScroll: true});
        if (document.activeElement === candidate) break;
      }
    }
    versions.set(host, version);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => initializeCountryFlags());
  else initializeCountryFlags();

  window.LineupsView = {update};
})();
