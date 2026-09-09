(() => {
  "use strict";
  const localFlag = /^\/images\/flags\/4x3\/(?:[a-z]{2}|gb-eng|gb-sct|gb-wls)\.svg$/;
  function node(tag, text, attribute, value = "") {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (attribute) element.setAttribute(attribute, value);
    return element;
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
      country.hidden = !model;
      const children = [];
      if (model && localFlag.test(model.flagPath || "")) {
        const flag = node("img", undefined, "data-event-person-flag");
        flag.src = model.flagPath; flag.alt = ""; flag.width = 24; flag.height = 18;
        children.push(flag, document.createTextNode(" "));
      }
      children.push(node("span", model?.label || "", "data-event-person-nationality"));
      country.replaceChildren(...children);
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
  window.EventDetailsView = Object.freeze({update});
})();
