(() => {
  "use strict";

  const form = document.querySelector("[data-batch-manifest-form]");
  if (!form) {
    return;
  }

  const fileInput = form.querySelector("[data-batch-files]");
  const declarations = Array.from(
    form.querySelectorAll("[data-batch-404-declaration]")
  );
  const summary = form.querySelector("[data-batch-manifest-summary]");
  const issueList = form.querySelector("[data-batch-manifest-issues]");
  const submitButton = form.querySelector("[data-batch-submit]");
  if (!fileInput || !summary || !issueList || !submitButton) {
    return;
  }

  const expectedCount = Number(form.dataset.expectedCount);
  const maximumFiles = Number(form.dataset.maximumFiles);
  const maximumFileBytes = Number(form.dataset.maximumFileBytes);
  const maximumTotalBytes = Number(form.dataset.maximumTotalBytes);
  const slots = new Map();

  declarations.forEach((declaration) => {
    const row = declaration.closest("[data-batch-evidence-row]");
    const state = row && row.querySelector("[data-batch-evidence-state]");
    slots.set(declaration.value, {declaration, row, state});
  });

  const pluralize = (count, singular, plural) =>
    `${count} ${count === 1 ? singular : plural}`;

  const setRowState = (slot, state, label) => {
    if (!slot.row || !slot.state) {
      return;
    }
    slot.row.dataset.evidenceState = state;
    slot.state.textContent = label;
  };

  const addIssue = (issues, message) => {
    if (!issues.includes(message)) {
      issues.push(message);
    }
  };

  const refresh = () => {
    const files = Array.from(fileInput.files || []);
    const filesByName = new Map();
    const invalidSizeNames = new Set();
    const issues = [];
    let totalBytes = 0;

    files.forEach((file) => {
      const sameName = filesByName.get(file.name) || [];
      sameName.push(file);
      filesByName.set(file.name, sameName);
      totalBytes += file.size;
      if (file.size === 0) {
        invalidSizeNames.add(file.name);
        addIssue(issues, `Fichier vide : ${file.name}.`);
      } else if (file.size > maximumFileBytes) {
        invalidSizeNames.add(file.name);
        addIssue(issues, `Fichier supérieur à 5 Mio : ${file.name}.`);
      }
    });

    if (files.length > maximumFiles) {
      addIssue(issues, `Trop de fichiers : ${files.length} pour une limite de ${maximumFiles}.`);
    }
    if (totalBytes > maximumTotalBytes) {
      addIssue(issues, "La taille cumulée dépasse 25 Mio.");
    }
    if (slots.size !== expectedCount) {
      addIssue(issues, "Le manifeste affiché ne correspond plus au plan préparé.");
    }

    const duplicateNames = new Set();
    filesByName.forEach((sameName, fileName) => {
      if (sameName.length > 1) {
        duplicateNames.add(fileName);
        addIssue(
          issues,
          `Fichier sélectionné plusieurs fois : ${fileName} (${sameName.length} fois).`
        );
      }
      if (!slots.has(fileName)) {
        addIssue(issues, `Nom non prévu par ce plan : ${fileName}.`);
      }
    });

    let declarationCount = 0;
    let conflictCount = 0;
    let missingCount = 0;

    slots.forEach((slot, fileName) => {
      const fileCount = (filesByName.get(fileName) || []).length;
      const declared = slot.declaration.checked;
      if (declared) {
        declarationCount += 1;
      }

      if (fileCount > 0 && declared) {
        conflictCount += 1;
        addIssue(issues, `Conflit : ${fileName} a un fichier et une déclaration 404.`);
        setRowState(slot, "conflict", "CONFLIT");
      } else if (duplicateNames.has(fileName)) {
        setRowState(slot, "duplicate", "DOUBLON");
      } else if (invalidSizeNames.has(fileName)) {
        setRowState(slot, "invalid", "INVALIDE");
      } else if (fileCount === 1) {
        setRowState(slot, "file", "FICHIER");
      } else if (declared) {
        setRowState(slot, "declared", "404");
      } else {
        missingCount += 1;
        addIssue(issues, `Preuve manquante : ${fileName}.`);
        setRowState(slot, "missing", "À FOURNIR");
      }
    });

    const inputCount = files.length + declarationCount;
    const exact = issues.length === 0 && inputCount === expectedCount;
    const parts = [
      pluralize(files.length, "fichier", "fichiers"),
      pluralize(declarationCount, "déclaration 404", "déclarations 404"),
      `${inputCount} saisie${inputCount === 1 ? "" : "s"} / ${expectedCount}`
    ];
    if (conflictCount > 0) {
      parts.push(pluralize(conflictCount, "conflit", "conflits"));
    }
    if (missingCount > 0) {
      parts.push(pluralize(missingCount, "preuve manquante", "preuves manquantes"));
    }

    summary.textContent = `${exact ? "Manifeste exact" : "Manifeste à corriger"} : ${parts.join(" · ")}.`;
    summary.classList.toggle("notice-safe", exact);
    summary.classList.toggle("notice-warning", !exact);
    summary.dataset.manifestValid = String(exact);

    issueList.replaceChildren();
    issues.slice(0, 8).forEach((issue) => {
      const item = document.createElement("li");
      item.textContent = issue;
      issueList.append(item);
    });
    if (issues.length > 8) {
      const item = document.createElement("li");
      item.textContent = `${issues.length - 8} autre(s) anomalie(s) sont signalées dans la liste des preuves.`;
      issueList.append(item);
    }
    issueList.hidden = issues.length === 0;

    submitButton.disabled = !exact;
    submitButton.setAttribute("aria-disabled", String(!exact));
    submitButton.title = exact
      ? "Manifeste exact"
      : "Corrigez le manifeste avant l’import local";
    return exact;
  };

  fileInput.addEventListener("change", refresh);
  declarations.forEach((declaration) => {
    declaration.addEventListener("change", refresh);
  });
  form.addEventListener("submit", (event) => {
    if (!refresh()) {
      event.preventDefault();
      summary.focus();
    }
  });

  refresh();
})();
