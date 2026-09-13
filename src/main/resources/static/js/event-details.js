(() => {
  "use strict";
  const localFlag = /^\/images\/flags\/4x3\/(?:[a-z]{2}|gb-eng|gb-sct|gb-wls)\.svg$/;

  function node(tag, text, attribute, value = "") {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (attribute) element.setAttribute(attribute, value);
    return element;
  }

  function countryLabel(label) {
    const fallback = node("span", undefined, "data-event-person-nationality");
    fallback.className = "country-fallback-label";
    const prefix = node("span", "Pays : ");
    prefix.className = "country-accessible-prefix";
    fallback.append(prefix, node("span", label, "data-event-person-nationality-label"));
    return fallback;
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
    root.querySelectorAll("[data-event-person-country]").forEach(country => {
      const flag = country.querySelector("[data-event-person-flag]");
      const path = flag?.getAttribute("src") || "";
      if (flag && localFlag.test(path)) trackCountryFlag(country, flag, path);
      else setCountryFlagState(country, "fallback");
    });
  }

  function update(host, view) {
    if (!view) return;
    let grid = host.querySelector(".detail-grid");
    if (!grid) {
      host.classList.add("event-information");
      host.append(node("h4", "Informations sur la rencontre"));
      grid = node("dl"); grid.className = "detail-grid"; host.append(grid);
    }
    for (const [key, role] of [["homeManager", `Entraîneur · ${view.homeTeam}`],
      ["awayManager", `Entraîneur · ${view.awayTeam}`], ["referee", "Arbitre"]]) {
      let row = grid.querySelector(`[data-event-person="${key}"]`);
      if (!row) {
        row = node("div", undefined, "data-event-person", key);
        row.append(node("dt", undefined, "data-event-person-role"));
        const value = node("dd");
        value.append(node("span", undefined, "data-event-person-name"), document.createTextNode(" "),
          node("span", undefined, "data-event-person-country"));
        row.append(value); grid.append(row);
      }
      row.querySelector("[data-event-person-role]").textContent = role;
      row.querySelector("[data-event-person-name]").textContent = view[key]?.name || "—";
      const country = row.querySelector("[data-event-person-country]");
      const model = view[key]?.country;
      const label = model?.label || "";
      const available = model?.available === true || (model?.available === undefined && label !== "");
      country.hidden = !available;
      country.removeAttribute("aria-label");
      if (!available) {
        country.replaceChildren();
        setCountryFlagState(country, "fallback");
        continue;
      }
      const path = model?.flagPath || "";
      const fallback = countryLabel(label);
      if (localFlag.test(path)) {
        const flag = node("img", undefined, "data-event-person-flag");
        flag.alt = ""; flag.width = 24; flag.height = 18;
        country.replaceChildren(flag, fallback);
        trackCountryFlag(country, flag, path);
      } else {
        country.replaceChildren(fallback);
        setCountryFlagState(country, "fallback");
      }
    }
    for (const [key, label] of [["competition", "Compétition"], ["round", "Tour"],
      ["season", "Saison"], ["venue", "Stade"]]) {
      let value = grid.querySelector(`[data-event-information="${key}"]`);
      if (!value) {
        const row = node("div"); value = node("dd", undefined, "data-event-information", key);
        row.append(node("dt", label), value); grid.append(row);
      }
      value.textContent = view[key] || "—";
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => initializeCountryFlags());
  else initializeCountryFlags();

  window.EventDetailsView = Object.freeze({update});
})();
