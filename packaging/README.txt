ETT HR portal @VERSION@ - portable edition
==========================================

Runs the ETT HR portal on this computer, with its own built-in database.
Use it to try out and demonstrate the portal. To run the portal for a whole
team on a server, use the server version (Docker) instead.


WHAT YOU NEED
-------------
- Java 25 or later (free). If it isn't installed, the portal opens the
  download page for you: https://adoptium.net/temurin/releases/?version=25
  Choose your system, "JRE", version 25, then run the installer.
- About 1 GB of free disk space.
- An authenticator app on your phone, such as Google Authenticator or
  Microsoft Authenticator. The portal uses two-step sign-in.


START THE PORTAL
----------------
@START@

A window opens and shows the portal's progress. Your browser then opens
the portal. The first start takes up to a minute.

Keep the window open while you use the portal. Close it to stop the portal.

The address is http://localhost:8080. If another program already uses
8080, the portal picks the next free number and shows it in the window.


SIGN IN
-------
Sample data is loaded on the first start: a sample client (Pellham & Rowe)
with its logo, a job with 15 scored CVs, partner centres, and candidates at
every step from shortlisting to offer. Interview reports are samples and are
labelled SAMPLE.

    Email:     charlotte.hughes@pellhamrowe.example
    Password:  Welcome@2026!

On first sign-in, scan the QR code with your authenticator app, then enter
the 6-digit code it shows.

Other sample users (same password):
    james.whitaker@pellhamrowe.example      HR admin
    oliver.grant@pellhamrowe.example        Recruiter
    emma.lawson@pellhamrowe.example         Recruiter
    daniel.fletcher@pellhamrowe.example     Hiring manager
    sarah.mitchell@pellhamrowe.example      Interviewer (sits on panels)
    thomas.reid@pellhamrowe.example         Interviewer

To add real people: Settings > Team and logins > Add people.


YOUR DATA
---------
Everything is kept in the "data" folder next to this file: the database,
uploaded CVs, logs and the encryption key.

- Start again from scratch: stop the portal, then delete the "data" folder.
- Back up: stop the portal, then copy the "data" folder.
- Keep data/encryption.key with the rest of the folder. Without it, everyone
  has to set up two-step sign-in again.

The portable edition doesn't send emails. Each email is written to
data/logs/ett-hr-portal.log instead, so you can see what candidates would
receive.


START WITHOUT SAMPLE DATA
-------------------------
Delete the "data" folder, set these environment variables, then start the
portal from a terminal with:   java -jar ett-hr-portal.jar --no-demo

    ETT_FIRST_COMPANY         Your company name
    ETT_FIRST_ADMIN_NAME      Your name
    ETT_FIRST_ADMIN_EMAIL     Your work email
    ETT_FIRST_ADMIN_PASSWORD  At least 12 characters


IF SOMETHING GOES WRONG
-----------------------
- "Needs Java 25": install Java from the page that opens, then start again.
- The browser didn't open: open the address shown in the window yourself.
- Anything else: send data/logs/ett-hr-portal.log to your ETT contact.


Copyright (c) 2026 Epic Talent Trust. All rights reserved. See LICENSE.txt.
Open-source components and their licences: THIRD_PARTY_NOTICES.md.
