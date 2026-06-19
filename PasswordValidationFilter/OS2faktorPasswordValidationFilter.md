# OS2faktor Login — Password Validation Filter

**Version:** 1.1.0
**Dato:** 18.05.2026
**Forfatter:** MPO

---

## 1 Indledning

Dette dokument beskriver hvordan man installerer og konfigurerer OS2faktors Password Filter.
Dokumentet er rettet mod it teknikere og driftsfolk der administrerer AD FS servere.

Formålet med OS2faktors Password Filter er at sikre sammenhæng mellem kodeord i AD og i OS2faktor. Filteret bruges i forbindelse med kodeordsskifte i Windows og sørger for at OS2faktor kan afgøre om kodeordet overholder kodeordsreglerne i OS2faktor, før kodeordet bliver godkendt og skiftet i AD.

---

## 2 Forudsætninger

### 2.1 Windows Server

Filteret skal installeres på alle Domain Controllere.
Filteret forudsætter desuden at .NET 8.0 Runtime er installeret på serveren.

---

## 3 Installation

Password Validation Filter distribueres som en exe installer. Installeren har ingen konfiguration og installerer programmet under:

```
C:\Program Files\Digital Identity\OS2faktorPasswordValidationFilter
```

Derudover bliver selve filteret (`OS2faktorPasswordValidationFilter.dll`) lagt i System32 så Windows kan indlæse filteret.

---

## 4 Konfiguration

I forbindelse med installationen tilføjer filteret sig selv under:

```
HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Control\Lsa
```

Her bliver filteret registreret under **Notification Packages**. Dette gør at LSA tager filteret i brug i forbindelse med kodeordsskifte. Hvis det ønskes at slå filteret fra midlertidigt kan man fjerne filteret under denne nøgle.

Filteret konfigureres 2 separate steder:

### 4.1 Registry

I registry konfigureres lokal logging samt forbindelsen til OS2faktor backend.
Alle indstillinger ligger under:

```
HKEY_LOCAL_MACHINE\SOFTWARE\DigitalIdentity\OS2faktorPasswordFilter
```

| Indstilling | Beskrivelse |
|---|---|
| `os2faktorBaseUrl` * | Denne indstilling skal pege på den URL som jeres OS2faktor IdP har. (typisk: `https://login-idp.kommunenavn.dk/`) |
| `clientApiKey` * | API nøgle som giver adgang til at kalde OS2faktor. Skriv til kontakt@digital-identity.dk for at få denne. |
| `LogPath` | Filsti til logging af handlinger udført af password filteret. Ingen logging er slået til som standard, men denne værdi kan sættes til en REG_SZ med en filsti. Fx: `C:\Logs\OS2faktor\PasswordFilterLog.txt` |
| `CallbackLogPath` | Filsti til logging af kontakten til OS2faktor fra password filteret. Ingen logging er slået til som standard, men denne værdi kan sættes til en REG_SZ med en filsti. Fx: `C:\Logs\OS2faktor\PasswordFilterCallbackLog.txt` |

\* skal sættes ved standard installation

### 4.2 Kodeordsregler

Selve regelsættet som filteret benytter er det som er opsat i ens OS2faktor selvbetjening. Regelændringer fra selvbetjeningens kodeordspolitik vil automatisk slå igennem i Password Filteret.

Der er derfor ikke behov for at konfigurere yderligere regler på serverne efter opsætningen.

---

## 5 Opgradering

> **Bemærk:** Opgradering af filteret kræver genstart af Domain Controlleren. Planlæg opgraderingen i et vedligeholdelsesvindue, da genstarten vil afbryde AD-tjenester midlertidigt.

Ved opgradering fra en tidligere version skal den eksisterende installation fjernes før den nye installeres.

### 5.1 Fejl: "Access is denied" under installation

Fra version 1.1.0 håndterer installeren dette automatisk via InnoSetups `restartreplace` flag. Hvis DLL'en er låst af LSASS køer installeren erstatningen til næste genstart og beder brugeren om at genstarte serveren.

Opstår fejlen alligevel med en ældre installer:

> *An error occurred while trying to replace the existing file: DeleteFile failed; code 5. Access is denied*

skyldes det at LSASS stadig har den gamle `OS2faktorPasswordValidationFilter.dll` i System32 låst i hukommelsen, selvom den forrige version er afinstalleret.

**Løsning:**

1. Omdøb den eksisterende DLL i System32 — fx til `OS2faktorPasswordValidationFilter.old.dll`
2. Kør installeren igen — den kan nu placere den nye DLL uden konflikter
3. Genstart serveren

Efter genstart indlæser LSASS den nye version og den omdøbte `.old`-fil kan slettes.
