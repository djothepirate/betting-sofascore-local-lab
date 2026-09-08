(() => {
  "use strict";
  const versions = new WeakMap();
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
    body.append(starters, substitutes);
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
    const info = create("span", "lineups-player-info");
    const meta = create("span", "lineups-player-meta");
    meta.append(create("span", "", "data-lineups-position"));
    info.append(create("strong", "lineups-name", "data-lineups-name"), meta);
    player.append(create("span", "lineups-number", "data-lineups-number"), info);
    return player;
  }

  function updatePlayer(node, player) {
    const number = text(player.shirtNumber, "—");
    const badge = node.querySelector("[data-lineups-number]");
    write(badge, number);
    const numberLabel = number === "—" ? "Numéro de maillot non renseigné" : `Numéro de maillot ${number}`;
    if (badge.getAttribute("aria-label") !== numberLabel) badge.setAttribute("aria-label", numberLabel);
    write(node.querySelector("[data-lineups-name]"), text(player.name, "Joueur non renseigné"));
    write(node.querySelector("[data-lineups-position]"), text(player.positionLabel, "Poste non renseigné"));
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
    if (focused && document.activeElement !== focused) {
      if (focused.isConnected && host.contains(focused)) focused.focus({preventScroll: true});
      else {
        const team = teams.find(panel => panel.dataset.lineupsTeam === focusTeam);
        const section = Array.from(team?.querySelectorAll("[data-lineups-section]") || [])
          .find(panel => panel.dataset.lineupsSection === focusSection);
        (section || team)?.querySelector(":scope > summary")?.focus({preventScroll: true});
      }
    }
    versions.set(host, version);
  }

  window.LineupsView = {update};
})();
