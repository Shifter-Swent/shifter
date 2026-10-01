# Shifter

## Pitch
Many organizations struggle to recruit enough volunteers and coordinate them efficiently before and during an event. Managing applications, requirements, availability, schedules, teams, documents, and check-ins often requires several different tools and a large amount of manual work.

Shifter is a volunteer recruitment and workforce management app designed to centralize this entire process. Organizations can publish volunteer missions with specific requirements, receive and manage applications, build teams, assign roles and tasks, and automatically generate schedules based on volunteer availability.

Volunteers can discover nearby missions, review their requirements, apply through a dedicated form, upload required documents or training certificates, manage their availability, and access all information related to their assigned missions.

During an event, managers can coordinate their teams, communicate updates, access briefings and documents, and scan volunteer QR codes for check-in. Essential information and QR check-in functionality remain available offline and synchronize automatically once an Internet connection is restored.

The result is a simpler way for organizations to recruit, organize, and coordinate volunteers from application to event completion.

## Features
- Discover nearby volunteer missions based on location
- Create and publish volunteer missions with specific requirements
- Application forms with required questions and document uploads
- Simple application workflow: Applied → Accepted / Rejected → Completed
- Volunteer profile with basic personal information and uploaded documents
- Volunteer availability calendar
- Personal calendar synchronization
- Automatic schedule generation based on availability, roles, and event requirements
- Team and role management
- Assignment of locations, positions, tasks, and shifts
- Checklists for teams and volunteers
- Event briefings and important instructions
- Document sharing
- Emergency contact information
- Direct messaging between organizers, managers, and volunteers
- Team chats for each event
- Announcements and schedule-change notifications
- Organizer dashboard for managing multiple events, teams, open positions, and schedules
- QR-code-based volunteer check-in
- Volunteer and organizer ratings after completed missions

## Split-App model
- Volunteer profiles, missions, applications, teams, roles, schedules, availability, documents, messages, ratings, and event management data are stored in Firebase.
- Essential information is also stored locally for offline access, including the volunteer's personal schedule, event address, assigned role, essential briefing information, and QR code.
- Managers can scan volunteer QR codes even without an Internet connection. Offline check-ins are stored locally and automatically synchronized with Firebase once connectivity is restored.
- Calendar availability can also be synchronized with the user's personal calendar when a connection is available.

## Multi-user support
Shifter supports three main user roles:

### Organizer
Organizers create and manage events and volunteer missions. They define requirements, review applications, accept or reject volunteers, create teams, assign managers, manage roles and tasks, generate schedules, upload documents, send announcements, and oversee the entire event.

### Manager
Managers are assigned by organizers to supervise specific teams or parts of an event. They can access their assigned volunteers, schedules, tasks, checklists, briefings, and documents. They can communicate with their team and scan volunteer QR codes for check-in, without having full control over the entire event.

### Volunteer
Volunteers can discover nearby missions, view mission details and requirements, submit applications, upload requested documents or certificates, manage their availability, synchronize their personal calendar, communicate with organizers and managers, and access their assigned schedules, tasks, documents, and QR code.

## Authentication
Users create and access their account using Google Sign-In.

## Sensor use
- **GPS** is used to display volunteer missions near the user's location and help volunteers discover relevant opportunities.
- **Camera access** is used by managers to scan volunteer QR codes during event check-in.

## Offline mode
Essential mission information remains accessible without an Internet connection.

Volunteers can access:
- Their personal schedule
- Event address
- Assigned role and position
- Essential briefing information
- Their QR code

Managers can continue scanning volunteer QR codes while offline. Check-in data is stored locally on the device and automatically synchronized with Firebase when the Internet connection becomes available again.

## Figma
SHifter Figma's model : https://www.figma.com/make/a5LSzNh9e0em1xIsjy1nIF/Shifter
