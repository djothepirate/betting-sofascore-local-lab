(() => {
  "use strict";

  const monitor = document.querySelector("[data-live-monitor]");
  if (!monitor) return;
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const revisions = new Map();
  const eventCampaigns = new Map();
  const tableVersions = new WeakMap();
  const incidentVersions = new WeakMap();
  const status = monitor.querySelector("[data-live-refresh-status]");
  let timer;
  let request;
  let generation = 0;
  if (monitor.dataset.liveCampaignId) {
    revisions.set(monitor.dataset.liveCampaignId, Number(monitor.dataset.liveRevision));
  }

  const text = (root, selector, value) => {
    const element = root.querySelector(selector);
    const next = value === null || value === undefined || value === "" ? "—" : String(value);
    if (element && element.textContent !== next) element.textContent = next;
  };
  const create = (tag, label, attribute) => {
    const element = document.createElement(tag);
    if (label !== undefined) element.textContent = label;
    if (attribute) element.setAttribute(attribute, "");
    return element;
  };
  const eventNodes = () => {
    const nodes = Array.from(monitor.querySelectorAll("[data-live-event-id]"));
    if (monitor.dataset.liveEventId) nodes.unshift(monitor);
    return nodes;
  };
  const campaignAuthority = campaign => {
    const startedAt = Date.parse(campaign.startedAt);
    return {campaignId: campaign.campaignId,
      priority: ["RUNNING", "CLEANUP_REQUIRED"].includes(campaign.state) ? 0 : Number.isFinite(startedAt) ? 1 : 2,
      startedAt: Number.isFinite(startedAt) ? startedAt : -Infinity,
      preparedAt: Date.parse(campaign.preparedAt)};
  };
  const precedes = (candidate, previous) => {
    if (candidate.priority !== previous.priority) return candidate.priority < previous.priority;
    if (candidate.startedAt !== previous.startedAt) return candidate.startedAt > previous.startedAt;
    if (candidate.preparedAt !== previous.preparedAt) return candidate.preparedAt > previous.preparedAt;
    return candidate.campaignId < previous.campaignId;
  };
  const updateSelection = () => {
    const inputs = Array.from(document.querySelectorAll('input[form="live-selection"][name="eventId"]'));
    const form = monitor.querySelector("[data-live-selection-form]");
    const maximum = Number(form?.dataset.liveSelectionMaximum ?? 100);
    const blocked = input => input.dataset.liveProviderEligible === "false" || input.dataset.liveCampaignBlocked === "true";
    const selected = inputs.filter(input => input.checked && !blocked(input));
    const checked = selected.length;
    const eligible = selected.filter(input => input.dataset.liveFinished !== "true").length;
    inputs.forEach(input => {
      input.disabled = maximum <= 0 || blocked(input) || !input.checked && input.dataset.liveFinished !== "true" && eligible >= maximum;
    });
    text(monitor, "[data-live-selection-count]", `${checked} rencontre${checked > 1 ? "s" : ""} sélectionnée${checked > 1 ? "s" : ""}`);
    text(monitor, "[data-live-eligible-count]", maximum <= 0 ? "Sélection indisponible : aucune capacité de collecte qualifiée."
      : `${eligible} rencontre${eligible > 1 ? "s" : ""} sélectionnée${eligible > 1 ? "s" : ""} admissible${eligible > 1 ? "s" : ""} / ${maximum}${eligible > maximum ? " — réduire la sélection" : ""}`);
    const button = monitor.querySelector("[data-live-prepare]");
    if (button) button.disabled = maximum <= 0 || checked === 0 || eligible > maximum;
  };
  document.querySelectorAll('input[form="live-selection"][name="eventId"]').forEach(input => {
    input.addEventListener("change", updateSelection);
  });
  updateSelection();

  function familyNode(container, family) {
    let section = Array.from(container.children).find(node => node.dataset.liveFamily === family.endpoint);
    if (section) return section;
    section = create("section");
    section.dataset.liveFamily = family.endpoint;
    section.append(create("h3", family.label));
    const result = create("p");
    ["outcome", "code", "scope"].forEach(key => {
      result.append(create("span", "", `data-live-family-${key}`), document.createTextNode(" "));
    });
    section.append(result, create("p", "", "data-live-family-previous"), create("p", "", "data-live-freshness"));
    const retry = create("p", "", "data-live-timeout-retry");
    retry.className = "notice notice-warning";
    const proof = create("dl", "", "data-live-timeout-proof");
    proof.className = "detail-grid";
    [["Fin du transport prouvée (UTC)", "ended"], ["Mode de fin observé", "end-reason"],
      ["Réutilisation du contexte vérifiée", "reusable"]].forEach(([label, key]) => {
      const item = create("div");
      item.append(create("dt", label), create("dd", "—", `data-live-timeout-${key}`));
      proof.append(item);
    });
    section.append(retry, proof);
    const metadata = create("dl");
    metadata.className = "detail-grid";
    [
      ["Dernière tentative réservée", "attempted"], ["Retard à l’autorisation transport", "delay"],
      ["Dernière réception", "received"], ["Dernier succès", "successful"],
      ["Dernier changement", "changed"], ["Âge depuis réception", "age"],
      ["Cadence cible", "family-interval"], ["Prochaine collecte", "family-next-due"],
      ["Retard de collecte", "family-lateness"],
      ["Complétude dernière tentative", "completeness"], ["Snapshot reçu / donnée lisible", "snapshots"],
      ["Occurrence reçue", "occurrence"], ["Parseur de la donnée lisible", "parser"],
      ["Hash source de la donnée lisible", "payload-hash"], ["Hash normalisé", "hash"]
    ].forEach(([label, key]) => {
      const item = create("div");
      const value = create("dd", "—", `data-live-${key}`);
      if (key === "hash" || key === "payload-hash") value.className = "mono hash-value";
      item.append(create("dt", label), value);
      metadata.append(item);
    });
    section.append(metadata);
    container.append(section);
    return section;
  }

  function renderTable(section, table) {
    if (!table || !Array.isArray(table.columns) || table.columns.length === 0) return;
    const version = JSON.stringify(table);
    if (tableVersions.get(section) === version) return;
    let body = section.querySelector("[data-live-table-body]");
    if (!body) {
      const details = create("details");
      const incidents = section.dataset.liveFamily === "EVENT_INCIDENTS";
      details.open = !incidents;
      if (incidents) details.setAttribute("data-incidents-technical", "true");
      details.append(create("summary", incidents ? "Tableau normalisé (technique)" : "Données normalisées"));
      const scroll = create("div");
      scroll.className = "table-scroll";
      const grid = create("table");
      const head = create("thead");
      const row = create("tr");
      table.columns.forEach(column => row.append(create("th", column)));
      head.append(row);
      body = create("tbody", undefined, "data-live-table-body");
      grid.append(head, body);
      scroll.append(grid);
      details.append(scroll, create("p", "", "data-live-empty"));
      section.append(details);
    }
    const rows = table.rows.map(values => {
      const row = create("tr");
      values.forEach(value => row.append(create("td", value)));
      return row;
    });
    body.replaceChildren(...rows);
    text(section, "[data-live-empty]", rows.length === 0 ? "Collection normalisée vide." : " ");
    tableVersions.set(section, version);
  }

  function incidentNode() {
    const item = create("li");
    item.className = "incidents-item";
    const minute = create("span", "—", "data-incident-minute");
    minute.className = "incidents-minute";
    const icon = create("span", "", "data-incident-icon");
    icon.className = "incidents-icon";
    icon.setAttribute("aria-hidden", "true");
    const content = create("div");
    content.className = "incidents-content";
    const heading = create("div");
    heading.className = "incidents-heading";
    const side = create("span", "", "data-incident-side");
    side.className = "incidents-side";
    heading.append(create("strong", "", "data-incident-label"), side);
    const player = create("p", undefined, "data-incident-player-line");
    player.className = "incidents-player-line";
    player.append(create("span", "", "data-incident-player"));
    content.append(heading, player);
    for (const [key, label, arrow] of [["in", "Entrée", "↗"], ["out", "Sortie", "↙"]]) {
      const change = create("div", undefined, `data-incident-${key}`);
      change.className = "incidents-change";
      const changeLabel = create("span");
      changeLabel.className = "incidents-change-label";
      const indicator = create("span", arrow);
      indicator.setAttribute("aria-hidden", "true");
      changeLabel.append(indicator, document.createTextNode(` ${label}`));
      change.append(changeLabel, create("span", "", `data-incident-player-${key}`));
      content.append(change);
    }
    for (const key of ["detail", "motif"]) {
      const note = create("p", "", `data-incident-${key}`);
      note.className = "incidents-note";
      content.append(note);
    }
    const score = create("span", "", "data-incident-score");
    score.className = "incidents-score";
    item.append(minute, icon, content, score);
    return item;
  }

  function renderIncidents(section, view) {
    let host = section.querySelector("[data-incidents]");
    if (!view || !Array.isArray(view.incidents)) { host?.remove(); return; }
    const version = JSON.stringify(view);
    if (host && incidentVersions.get(host) === version) return;
    if (!host) {
      host = create("div", undefined, "data-incidents"); host.className = "incidents-view";
      const technical = section.querySelector("[data-incidents-technical]");
      section.insertBefore(host, technical?.parentElement === section ? technical : null);
    }
    const focused = host.contains(document.activeElement) ? document.activeElement : null;
    const focusedPeriod = focused?.closest("[data-incidents-period]")?.dataset.incidentsPeriod;
    const existing = new Map(Array.from(host.querySelectorAll("[data-incident-key]"), node => [node.dataset.incidentKey, node]));
    let disclosure = host.querySelector("[data-incidents-disclosure]");
    if (!disclosure) {
      disclosure = create("details", undefined, "data-incidents-disclosure"); disclosure.className = "incidents-disclosure"; disclosure.open = true;
      const summary = create("summary", "Incidents du match ");
      const count = create("span", "0", "data-incidents-count"); count.className = "incidents-count";
      summary.append(count);
      const empty = create("p", "La liste source est explicitement vide.", "data-incidents-empty"); empty.className = "incidents-empty";
      disclosure.append(summary, empty, create("div", undefined, "data-incidents-periods"));
      host.replaceChildren(disclosure);
    }
    text(disclosure, "[data-incidents-count]", String(view.incidents.length));
    disclosure.querySelector("[data-incidents-empty]").hidden = view.incidents.length > 0;
    let periods = Array.isArray(view.periods) ? view.periods : [];
    const indexes = periods.flatMap(period => Array.isArray(period.incidentIndexes) ? period.incidentIndexes : []);
    const keys = periods.map(period => period.key);
    if (indexes.length !== view.incidents.length || new Set(indexes).size !== indexes.length
      || indexes.some(index => !Number.isInteger(index) || index < 0 || index >= view.incidents.length)
      || new Set(keys).size !== keys.length || keys.some(key => typeof key !== "string" || !/^[A-Z_]+$/.test(key))) {
      periods = view.incidents.length ? [{key: "ALL", label: "Incidents", incidentIndexes: view.incidents.map((_, index) => index)}] : [];
    }
    const container = disclosure.querySelector("[data-incidents-periods]");
    const existingPeriods = new Map(Array.from(container.children, node => [node.dataset.incidentsPeriod, node]));
    const desiredPeriods = periods.map((period, periodIndex) => {
      let panel = existingPeriods.get(period.key);
      existingPeriods.delete(period.key);
      if (!panel) {
        panel = create("details", undefined, "data-incidents-period"); panel.className = "incidents-period";
        panel.dataset.incidentsPeriod = period.key; panel.open = true;
        const summary = create("summary");
        const count = create("span", "0", "data-incidents-period-count"); count.className = "incidents-count";
        summary.append(create("span", "", "data-incidents-period-label"), count);
        const list = create("ol", undefined, "data-incidents-list"); list.className = "incidents-list";
        list.setAttribute("aria-label", "Incidents dans l’ordre de l’observation"); panel.append(summary, list);
      }
      text(panel, "[data-incidents-period-label]", period.label);
      text(panel, "[data-incidents-period-count]", String(period.incidentIndexes.length));
      const list = panel.querySelector("[data-incidents-list]");
      const desired = period.incidentIndexes.map((index, position) => {
        const incident = view.incidents[index], key = String(index);
        // This is an occurrence ordinal within the source observation, not a provider identity.
        const node = existing.get(key) || incidentNode(); existing.delete(key);
      node.dataset.incidentKey = key;
      node.dataset.incidentTone = incident.tone;
      node.dataset.incidentTeamSide = incident.teamSide;
      for (const [field, selector] of [["minuteLabel", "minute"], ["typeLabel", "label"], ["icon", "icon"],
        ["playerLabel", "player"], ["scoreLabel", "score"], ["detailLabel", "detail"],
        ["motifLabel", "motif"], ["playerInLabel", "player-in"], ["playerOutLabel", "player-out"]])
        text(node, `[data-incident-${selector}]`, incident[field]);
      const sideLabel = incident.teamSide === "HOME" ? "Domicile" : incident.teamSide === "AWAY" ? "Extérieur" : "";
      text(node, "[data-incident-side]", !sideLabel || incident.teamLabel === sideLabel
        ? incident.teamLabel : `${sideLabel} · ${incident.teamLabel}`);
      node.querySelector("[data-incident-side]").hidden = incident.tone === "period";
      node.querySelector("[data-incident-player-line]").hidden = incident.playerLabel === "—"
        || incident.tone === "substitution" && (incident.playerInLabel !== "—" || incident.playerOutLabel !== "—");
      node.querySelector("[data-incident-in]").hidden = incident.playerInLabel === "—";
      node.querySelector("[data-incident-out]").hidden = incident.playerOutLabel === "—";
      node.querySelector("[data-incident-detail]").hidden = incident.detailLabel === "—";
      node.querySelector("[data-incident-motif]").hidden = incident.motifLabel === "—";
      const score = node.querySelector("[data-incident-score]");
      score.hidden = incident.scoreLabel === "—";
      score.setAttribute("aria-label", `Score : ${incident.scoreLabel}`);

        if (list.children.item(position) !== node) list.insertBefore(node, list.children.item(position));
        return node;
      });
      const kept = new Set(desired);
      Array.from(list.children).forEach(node => { if (!kept.has(node)) node.remove(); });
      if (container.children.item(periodIndex) !== panel) container.insertBefore(panel, container.children.item(periodIndex));
      return panel;
    });
    existing.forEach(node => node.remove()); existingPeriods.forEach(node => node.remove());
    if (focused) {
      const period = desiredPeriods.find(node => node.dataset.incidentsPeriod === focusedPeriod);
      for (const candidate of [focused, period?.querySelector(":scope > summary"), disclosure.querySelector(":scope > summary")]) {
        if (!candidate?.isConnected || !host.contains(candidate) || candidate.getClientRects().length === 0) continue;
        candidate.focus({preventScroll: true}); if (document.activeElement === candidate) break;
      }
    }
    incidentVersions.set(host, version);
  }

  function renderFamily(container, family) {
    const section = familyNode(container, family);
    text(section, "[data-live-family-outcome]", family.outcome);
    text(section, "[data-live-family-code]", family.code);
    text(section, "[data-live-family-scope]", family.scope);
    const retry = section.querySelector("[data-live-timeout-retry]");
    if (retry) {
      retry.hidden = family.code !== "PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED" || !family.schedule?.nextDueAt;
      retry.textContent = "Le délai de cette tentative a été dépassé. Sa fin et le nettoyage local ont été vérifiés. Une prochaine collecte est différée ; aucune nouvelle donnée n’a été reçue intégralement.";
    }
    const proof = section.querySelector("[data-live-timeout-proof]");
    if (proof) proof.hidden = !family.transport;
    text(section, "[data-live-timeout-ended]", family.transport?.exchangeEndedAt);
    text(section, "[data-live-timeout-end-reason]", family.transport?.exchangeEndReason);
    text(section, "[data-live-timeout-reusable]", family.transport?.contextReusable === true ? "Oui" : "Non établie");
    if (family.freshness) {
      text(section, "[data-live-freshness]", family.freshness.label);
      const freshness = section.querySelector("[data-live-freshness]");
      if (freshness) freshness.className = family.freshness.state === "STALE" ? "notice notice-warning" : "muted";
    }
    text(section, "[data-live-attempted]", family.lastAttemptAt);
    text(section, "[data-live-delay]", family.authorizationDelayMillis === null
      || family.authorizationDelayMillis === undefined ? "—" : `${family.authorizationDelayMillis} ms`);
    text(section, "[data-live-family-previous]", family.previousData
      ? "Dernière donnée lisible conservée : elle ne décrit pas la dernière tentative." : " ");
    text(section, "[data-live-received]", family.lastReceivedAt);
    text(section, "[data-live-successful]", family.lastSuccessfulAt);
    text(section, "[data-live-changed]", family.lastChangedAt);
    text(section, "[data-live-family-interval]", family.schedule ? `${family.schedule.intervalSeconds} s` : "—");
    text(section, "[data-live-family-next-due]", family.schedule
      ? family.schedule.nextDueAt || "Aucune collecte programmée" : "—");
    text(section, "[data-live-family-lateness]", family.schedule?.nextDueAt
      ? `${Math.ceil(family.schedule.latenessMillis / 1000)} s` : "—");
    text(section, "[data-live-completeness]", family.completeness
      ? `${family.completeness}${family.completenessScore === null ? "" : ` · ${family.completenessScore} %`}` : "—");
    text(section, "[data-live-snapshots]", `${family.receivedSnapshotId ?? "—"} / ${family.dataSnapshotId ?? "—"}`);
    text(section, "[data-live-parser]", family.parserVersion);
    text(section, "[data-live-occurrence]", family.receivedOccurrenceId);
    text(section, "[data-live-payload-hash]", family.payloadSha256);
    text(section, "[data-live-hash]", family.normalizedSha256);
    const age = section.querySelector("[data-live-age]");
    if (age) {
      age.dataset.liveReceivedAt = family.lastReceivedAt || "";
      age.dataset.liveAgeFrozen = String(family.freshness?.frozen === true);
      age.dataset.liveAgeAsOf = family.freshness?.ageAsOf || "";
    }
    if (family.endpoint === "EVENT_DETAILS" && window.EventDetailsView) {
      let host = section.querySelector("[data-event-details]");
      if (!family.eventDetails) host?.remove();
      else {
        if (!host) {
          host = create("section", undefined, "data-event-details");
          section.append(host);
        }
        window.EventDetailsView.update(host, family.eventDetails);
      }
    } else if (family.endpoint === "EVENT_STATISTICS" && window.StatisticsView) {
      let host = section.querySelector("[data-statistics]");
      if (!family.statistics) {
        host?.remove();
      } else {
        if (!host) {
          host = create("div", undefined, "data-statistics");
          section.append(host);
        }
        window.StatisticsView.update(host, family.statistics);
      }
    } else if (family.endpoint === "EVENT_LINEUPS" && window.LineupsView) {
      let host = section.querySelector("[data-lineups]");
      if (!family.lineups) {
        host?.remove();
      } else {
        if (!host) {
          host = create("div", undefined, "data-lineups");
          section.append(host);
        }
        window.LineupsView.update(host, family.lineups);
      }
    } else if (family.endpoint === "EVENT_INCIDENTS") {
      renderIncidents(section, family.incidents);
      if (family.incidents) {
        renderTable(section, family.table);
      } else {
        section.querySelector("[data-incidents-technical]")?.remove();
        tableVersions.delete(section);
      }
    } else {
      renderTable(section, family.table);
    }
  }

  function renderCampaign(campaign) {
    if (!campaign || !uuid.test(campaign.campaignId) || !Number.isSafeInteger(campaign.revision)) return;
    // The process observation can change while the durable ledger revision remains equal.
    if (campaign.revision < (revisions.get(campaign.campaignId) ?? -1)) return;
    revisions.set(campaign.campaignId, campaign.revision);
    const runtime = campaign.runtimeStatus;
    const collectionStopped = runtime?.collectionStopped === true;
    const cleanupPending = runtime?.cleanupPending === true;
    const cleanupInProgress = runtime?.cleanupInProgress === true;
    const renderRuntime = root => {
      const notice = root.querySelector("[data-live-runtime-status]");
      if (notice) {
        notice.hidden = !runtime;
        notice.textContent = runtime?.label || "";
      }
    };
    if (monitor.dataset.liveCampaignId === campaign.campaignId) {
      renderRuntime(monitor);
      const diagnostics = monitor.querySelector("[data-live-diagnostics]");
      if (diagnostics) {
        diagnostics.hidden = !runtime?.firstFailure && !runtime?.cleanupFailure;
        for (const key of ["firstFailure", "cleanupFailure"]) {
          const section = diagnostics.querySelector(`[data-live-diagnostic="${key}"]`);
          if (!section) continue;
          const failure = runtime?.[key];
          section.hidden = !failure;
          text(section, "[data-live-diagnostic-time]", failure?.occurredAt);
          text(section, "[data-live-diagnostic-phase]", failure?.phase);
          text(section, "[data-live-diagnostic-code]", failure?.code);
          text(section, "[data-live-diagnostic-attempt]", failure?.attemptId);
          text(section, "[data-live-diagnostic-endpoint]", failure?.endpoint);
          const transport = failure?.transport;
          text(section, "[data-live-diagnostic-transport-phase]", transport?.phase);
          text(section, "[data-live-diagnostic-timeout]", Number.isSafeInteger(transport?.requestTimeoutMillis)
            ? `${transport.requestTimeoutMillis} ms` : null);
          text(section, "[data-live-diagnostic-requested-at]", transport?.requestedAt);
          text(section, "[data-live-diagnostic-headers-at]", transport?.headersReceivedAt);
          text(section, "[data-live-diagnostic-http-status]", transport?.httpStatus);
          text(section, "[data-live-diagnostic-retry-after]", transport?.retryAfterNotBefore);
          text(section, "[data-live-diagnostic-complete]", transport?.responseComplete === true ? "Oui"
            : transport?.responseComplete === false ? "Non" : null);
          text(section, "[data-live-diagnostic-ended]", transport?.exchangeEndedAt);
          text(section, "[data-live-diagnostic-end-reason]", transport?.exchangeEndReason);
          text(section, "[data-live-diagnostic-reusable]", transport?.contextReusable === true ? "Oui" : "Non établie");
        }
      }
      text(monitor, "[data-live-campaign-state]", campaign.state);
      text(monitor, "[data-live-campaign-reason]", campaign.reason);
      text(monitor, "[data-live-started-at]", campaign.startedAt || (campaign.state === "PREPARED" ? "En attente de lancement" : "Non lancée"));
      text(monitor, "[data-live-ends-at]", campaign.endsAt || (campaign.state === "PREPARED" ? "Fixée au lancement" : "—"));
      text(monitor, "[data-live-calls]", `${campaign.reservedCalls} / ${campaign.maximumCalls}`);
      text(monitor, "[data-live-bytes]", `${campaign.receivedBytes} / ${campaign.maximumBytes}`);
      if (campaign.cadence) text(monitor, "[data-live-autonomy]",
        campaign.runtimeStatus?.collectionStopped ? "Collecte arrêtée"
          : `${Math.floor(campaign.cadence.estimatedRemainingSeconds / 60)} minutes environ`);
      const globalStop = monitor.querySelector("[data-live-global-stop-form]");
      if (globalStop) {
        globalStop.hidden = campaign.state !== "RUNNING" && !cleanupPending;
        const button = globalStop.querySelector("button");
        if (button) {
          button.disabled = cleanupInProgress || collectionStopped && !cleanupPending
            || campaign.state !== "RUNNING" && !cleanupPending;
          button.textContent = cleanupInProgress ? "Clôture locale en cours"
            : cleanupPending ? "Finaliser la clôture locale" : "Arrêter toute la campagne";
        }
      }
      const preparation = monitor.querySelector("[data-live-preparation]");
      if (preparation) preparation.hidden = campaign.state !== "PREPARED";
    }
    campaign.events.forEach(event => {
      if (!uuid.test(event.canonicalEventId)) return;
      const previous = eventCampaigns.get(event.canonicalEventId);
      const authority = campaignAuthority(campaign);
      if (previous && previous.campaignId !== campaign.campaignId && !precedes(authority, previous)) return;
      eventCampaigns.set(event.canonicalEventId, authority);
      eventNodes().filter(node => node.dataset.liveEventId === event.canonicalEventId).forEach(node => {
        renderRuntime(node);
        const displayedAt = Date.parse(node.dataset.liveCanonicalReceivedAt);
        const incomingAt = Date.parse(event.sourceReceivedAt);
        const canReplaceCanonical = !node.hasAttribute("data-live-mirror-canonical")
          || event.canonicalCurrent === true && Number.isFinite(incomingAt)
            && (!Number.isFinite(displayedAt) || incomingAt >= displayedAt);
        if (canReplaceCanonical) {
          text(node, "[data-live-sport-status]", event.sportStatusLabel || event.sportStatus);
          text(node, "[data-live-score]", event.score);
          if (Number.isFinite(incomingAt)) node.dataset.liveCanonicalReceivedAt = event.sourceReceivedAt;
          if (event.sourceSnapshotId !== null && event.sourceSnapshotId !== undefined) {
            text(node, "[data-live-source-ref]", `snapshot:${event.sourceSnapshotId}`);
            text(node, "[data-live-source-received]", event.sourceReceivedAt);
          }
        }
        text(node, "[data-live-campaign-score]", event.score);
        text(node, "[data-live-event-state]", event.state);
        const selection = node.querySelector('input[form="live-selection"][name="eventId"]');
        if (selection) {
          selection.dataset.liveCampaignBlocked = String(event.selectionBlocked === true);
          if (canReplaceCanonical) selection.dataset.liveFinished = String(["finished", "postponed"].includes(event.sportStatus));
          if (selection.dataset.liveProviderEligible !== "true" || event.selectionBlocked === true) selection.checked = false;
          const blocked = node.querySelector("[data-live-selection-blocked]");
          if (blocked) blocked.hidden = event.selectionBlocked !== true;
          updateSelection();
        }
        text(node, "[data-live-sport-context]", event.state === "STOPPED_ALREADY_FINISHED"
          ? "Le statut sportif ci-dessus décrit l’observation figée à la préparation. La rencontre a été constatée terminée localement au lancement ; aucune nouvelle collecte n’a eu lieu."
          : event.state === "STOPPED_ALREADY_POSTPONED"
            ? "Le statut sportif ci-dessus décrit l’observation figée à la préparation. La rencontre a été constatée reportée (postponed) localement au lancement ; aucune nouvelle collecte n’a eu lieu."
            : event.state === "STOPPED_POSTPONED"
              ? "La rencontre a été signalée reportée (postponed) par J4. Son suivi est arrêté ; les observations déjà reçues restent consultables." : " ");
        text(node, "[data-live-event-reason]", event.reason);
        text(node, "[data-live-next-due]", event.nextDueAt);
        text(node, "[data-live-event-calls]", `${event.reservedCalls} / ${event.maximumCalls}`);
        text(node, "[data-live-missed-cycles]", event.missedCycles);
        text(node, "[data-live-final-complete]", event.finalComplete ? "Oui" : "Non établi");
        const link = node.querySelector("[data-live-link]");
        if (link) {
          const stateUrl = new URL(monitor.dataset.liveStateUrl, location.href);
          const prefix = stateUrl.pathname.split("/events")[0];
        link.href = `${prefix}/live-campaigns/${campaign.campaignId}?eventId=${event.canonicalEventId}#live-event-${event.canonicalEventId}`;
          link.hidden = false;
        }
        node.querySelectorAll("[data-live-stop-form]").forEach(form => {
          form.hidden = collectionStopped || campaign.state !== "RUNNING"
            || event.state.startsWith("STOPPED") || event.state === "FINISHED_CONFIRMED";
          const button = form.querySelector("button");
          if (button) button.disabled = form.hidden;
        });
        const families = node.querySelector("[data-live-families]");
        if (families) event.families.forEach(family => renderFamily(families, family));
      });
    });
  }

  function updateAges() {
    monitor.querySelectorAll("[data-live-age]").forEach(element => {
      const received = Date.parse(element.dataset.liveReceivedAt);
      const now = element.dataset.liveAgeFrozen === "true" ? Date.parse(element.dataset.liveAgeAsOf) : Date.now();
      element.textContent = Number.isFinite(received)
        && Number.isFinite(now) ? `${Math.max(0, Math.floor((now - received) / 1000))} s` : "—";
    });
  }

  async function refresh() {
    if (document.hidden || request) return;
    const currentGeneration = generation;
    const controller = new AbortController();
    request = controller;
    let timedOut = false;
    const deadline = setTimeout(() => { timedOut = true; controller.abort(); }, 10000);
    try {
      const url = new URL(monitor.dataset.liveStateUrl, location.href);
      if (url.origin !== location.origin) throw new Error("LOCAL_URL_REQUIRED");
      if (url.pathname.endsWith("/events/state")) {
        eventNodes().forEach(node => {
          if (uuid.test(node.dataset.liveEventId)) url.searchParams.append("eventId", node.dataset.liveEventId);
        });
      }
      const response = await fetch(url, {method: "GET", mode: "same-origin", credentials: "same-origin",
        cache: "no-store", redirect: "error", headers: {Accept: "application/json"}, signal: controller.signal});
      if (!response.ok) throw new Error("LOCAL_READ_UNAVAILABLE");
      const state = await response.json();
      if (document.hidden || currentGeneration !== generation) return;
      const campaigns = Array.isArray(state) ? state : [state];
      campaigns.forEach(renderCampaign);
      if (status) status.textContent = campaigns.length === 0 ? "Aucune campagne live enregistrée pour cette sélection."
        : `Lecture locale à ${new Date().toLocaleTimeString("fr-FR", {timeZone: "Europe/Paris"})} · Europe/Paris`;
    } catch (error) {
      if (currentGeneration === generation && !document.hidden && status && (timedOut || error.name !== "AbortError"))
        status.textContent = timedOut
          ? "Lecture locale interrompue après dix secondes ; dernières données conservées, nouvelle tentative dans cinq secondes."
          : "Lecture locale indisponible ; les dernières données affichées sont conservées.";
    } finally {
      clearTimeout(deadline);
      request = null;
      updateAges();
      if (!document.hidden) timer = setTimeout(refresh, 5000);
    }
  }

  document.addEventListener("visibilitychange", () => {
    clearTimeout(timer);
    generation += 1;
    if (document.hidden) {
      if (request) request.abort();
      if (status) status.textContent = "Lecture locale suspendue pendant que l’onglet est masqué.";
    } else if (!request) refresh();
  });
  window.addEventListener("pagehide", () => {
    clearTimeout(timer);
    generation += 1;
    if (request) request.abort();
  });
  updateAges();
  refresh();
})();
