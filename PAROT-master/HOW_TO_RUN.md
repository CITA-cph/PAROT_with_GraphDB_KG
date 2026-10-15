# How to run a PAROT query

## 1. Prerequisites

- Java (JDK) installed — the script assumes Eclipse Adoptium JDK 25. Download from https://adoptium.net if needed.
- Access to the GraphDB instance (must be running and reachable on the network).
- The `config.properties` file in the project folder with the correct GraphDB URL and credentials (ask the project owner for this file — it is not in the repository).

---

## 2. Edit the paths in `run.ps1`

Open `run.ps1` in any text editor and update the three lines at the top of the file to match your machine:

```powershell
$JAVA  = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"
$BASE  = "C:\Users\garo\OneDrive - ...\PAROT-master"
```

- `$JAVA` — path to `java.exe` on your machine. To find it, open PowerShell and type `where.exe java` or `Get-Command java`.
- `$BASE` — full path to the `PAROT-master` folder on your machine.

Leave everything else unchanged.

---

## 3. Open PowerShell

In VSCode: open the terminal with `` Ctrl+` `` and make sure it says `powershell` in the dropdown.

Or open Windows PowerShell from the Start menu.

Navigate to the project folder:

```powershell
cd "C:\path\to\PAROT-master"
```

---

## 4. Run a query

**Default test query** (elongation of hemp fiber):

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

**Custom query:**

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 "What is the tensile strength of flax?"
```

Replace the text in quotes with any natural language question about the knowledge graph.

---

## 5. What to expect

The script will:
1. Compile the Java source files
2. Print the query it is sending
3. Show the full PAROT log, ending with the generated SPARQL and the result

A successful result looks like:
```
[RESULT]
[1] score=...
select DISTINCT ?elongation where ...

GraphDB SPARQL:
select DISTINCT ?elongation where { ... }
```

If the result is empty, PAROT will automatically retry with a label-based fallback and then a 2-hop traversal query. If all three return empty, the data either does not exist in the KG or the entity name in your question did not match any known label — try rephrasing.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `java.exe` not found | Update `$JAVA` in `run.ps1` to the correct path |
| `compile.bat` fails | Check that the `lib\` folder contains all `.jar` files |
| `config.properties` not found | Copy the file into the `PAROT-master` folder |
| GraphDB connection refused | Check the GraphDB URL in `config.properties` and that the server is running |
| All three queries return empty | The entity or property may not exist in the KG — check with a manual SPARQL query in GraphDB Workbench |
