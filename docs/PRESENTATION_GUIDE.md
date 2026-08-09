# CSE 215 presentation guide

## Preparation

- Carry a printed copy of the final six-page A4 report on August 25, 2026.
- Install JDK 25 and Apache Ant, or open the project in Apache NetBeans.
- Run `ant clean test-quality jar` before presentation day.
- Keep the entire project folder writable; Wealthora uses its `data/` folder.
- If live email verification will be shown, start a configured HTTPS OTP relay
  and use a controlled `@northsouth.edu` mailbox. Otherwise use the offline
  OWNER bootstrap and recovery flow without claiming live mail delivery.
- Before the defense, privately confirm the branded Create Account and Forgot
  Password emails in the controlled mailbox. Never project or capture the OTP.
- Configure Gmail privately before the defense. During the presentation, use
  **Start Wealthora.cmd** or keep **Start OTP Relay for NetBeans.cmd** open while
  running the project with F6; never show the local encrypted configuration.

Run from the project root:

```powershell
java -jar dist\Wealthora.jar
```

Preferred Windows defense launch: double-click **`Start Wealthora.cmd`**.

Defense explanation:

> Wealthora is developed entirely in Java using Swing and can be built and run
> directly from NetBeans. Its one-click launcher starts the Wealthora and OTP
> relay Java JARs automatically. The Gmail App Password is entered once and
> stored locally using Windows user-specific encryption; it is never stored in
> the source code or submission package.

## Presentation allocation

This allocation covers presentation and demonstration responsibilities only;
it does not assign ownership of the implementation.

| Presenter | Assigned coverage |
| --- | --- |
| Moon (Shibli Rahman Moon) | Introduction, problem statement, objectives, key features, high-level architecture and workflow, GUI and user journey, authentication and OTP overview, dashboard demonstration, limitations, Future Scope, and conclusion |
| Nafij (Md. Nafij Jaman Rabbi) | OOP concepts, class design, transactions, budget, analytics, and the related demonstration |
| Monimul (Md. Monimul Haque) | Local persistence, search and filtering, import and export, validation, security controls, testing, and the related demonstration |
| All | Final review, live demonstration, and Q&A |

## Suggested live demonstration sequence

1. Keep the printed six-page report ready, launch Wealthora, and sign in as the
   local OWNER.
2. Select **Presentation Data → Load Presentation Data**. Explain that the
   action is explicit, idempotent, and tracked by an exact per-user manifest.
3. Open Dashboard and point out current balance, income, expense, and cash-flow
   summaries.
4. Open Transactions, add an income and expense, then edit and delete records
   with validation and confirmation.
5. Open **Entry → Voice Quick Entry** (`Ctrl+Shift+V`), enter or speak an English
   or Banglish command, review the parsed draft, and confirm before saving.
6. Use **Data** to export transactions or a PDF report. Briefly show validated
   backup/import support.
7. Open **Profile → Admin Console** and explain OWNER/ADMIN/USER authorization,
   account status, and audit records.
8. Show **Create Account** and explain the registration transaction: code send,
   resend cooldown, newest-code rule, verification, then local account creation.
9. Open **Forgot Password?** and show the choice between email OTP reset and the
   independent offline recovery question.
10. Close and reopen the application to show project-local persistence.
11. Use [OOP_MAPPING.md](OOP_MAPPING.md) to connect visible behavior to
    abstraction, inheritance, polymorphism, encapsulation, interfaces,
    collections, and exception handling.

The configured presentation laptop is the live-email path. If network or Gmail
delivery is unavailable, continue with the existing-user sign-in, finance, and
protected offline-recovery flow; do not improvise credentials or security
bypasses.

## Safe cleanup

Choose **Presentation Data → Remove Presentation Data**. Wealthora removes only
the transactions recorded in its presentation manifest and archives only the
accounts it created. Manually entered records and preexisting name/identifier
collisions are preserved.

Use invented financial values and a controlled mailbox. Never expose a real
password, recovery answer, SMTP credential, OTP signing secret, personal CSV,
or backup during the presentation.
