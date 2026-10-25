# CricAuction Backend API Specification & System Architecture

## 1. System Overview & Role Matrix

CricAuction is a mobile-first platform for cricket tournament management, live player auctions, and real-time scoring. The application serves three distinct user roles, each with dedicated dashboards, workflows, and permissions:

```mermaid
flowchart TD
    User([User]) --> Auth[Authentication & OTP Verification]
    Auth --> RoleChoice{User Role Selection}
    RoleChoice -->|Organizer| OrgDash[Organizer Dashboard]
    RoleChoice -->|Team Leader / Captain| CapDash[Captain Dashboard]
    RoleChoice -->|Player| PlayDash[Player Dashboard]

    OrgDash --> TournSetup[Tournament Creation & Rules]
    TournSetup --> TeamMgmt[Team & Captain Management]
    TeamMgmt --> PlayerMgmt[Player Capacity & Approval]
    PlayerMgmt --> LiveAuctionOrg[Auction Control Room]

    CapDash --> JoinTeam[Accept Invite & Claim Team]
    JoinTeam --> SquadView[Squad Budget & Roster]
    SquadView --> LiveAuctionBid[Live Auction Bidding Room]

    PlayDash --> TournReg[Tournament Registration & Profile]
    TournReg --> StatusTrack[Auction Journey Tracking]
    StatusTrack --> LiveMatch[Assigned Squad & Fixtures]
```

### Role Capabilities Summary
| Feature Module | Tournament Organizer | Team Leader (Captain) | Player |
| :--- | :---: | :---: | :---: |
| Account Registration & Auth | Full | Full | Full |
| Role Selection & Profile Setup | Full | Full | Full |
| Create & Configure Tournaments | Full (Owner) | View Only | View Only |
| Team & Captain Management | Full (Add/Edit/Invite) | Manage Own Team | View Teams |
| Player Registrations Management | Review, Approve, Price | View Squad | Register, Track |
| Live Auction Room | Controller (Hammer, Pass, Undo) | Active Bidder (Purse-checked) | Spectator |
| Matches & Scoring | Create & Score | View Fixtures | View Fixtures |

---

## 2. Global Conventions

- **Base URL**: `https://api.cricauction.app/api/v1`
- **WebSocket Gateway**: `wss://api.cricauction.app/ws/v1/auction`
- **Standard Authentication**: Bearer Token in `Authorization: Bearer <JWT_ACCESS_TOKEN>`
- **Content Types**: `application/json` (or `multipart/form-data` for file uploads)
- **Standard Response Envelope**:
```json
{
  "success": true,
  "message": "Action completed successfully",
  "data": {},
  "timestamp": "2026-10-24T14:00:00Z"
}
```
- **Standard Error Envelope**:
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Tournament end date must be on or after start date.",
    "details": [
      { "field": "endDate", "issue": "Must be >= startDate" }
    ]
  },
  "timestamp": "2026-10-24T14:00:00Z"
}
```

---

## 3. Module Breakdown & API Catalogue (62 Endpoints)

---

### Module 1: Authentication & Authorization (10 APIs)

#### 1. User Sign Up
- **Method**: `POST`
- **Endpoint**: `/auth/signup`
- **Access**: Public
- **Description**: Registers a new user account with full name, email, mobile, and password.
- **Request Payload**:
```json
{
  "fullName": "Hardik Patel",
  "email": "hardik@example.com",
  "mobile": "+919876543210",
  "password": "StrongPassword123!",
  "confirmPassword": "StrongPassword123!",
  "agreedToTerms": true
}
```
- **Response (201 Created)**:
```json
{
  "success": true,
  "message": "Account created. Please verify your mobile number.",
  "data": {
    "userId": "usr_948214",
    "mobile": "+919876543210",
    "email": "hardik@example.com",
    "isVerified": false,
    "otpExpirySeconds": 60
  }
}
```

#### 2. User Sign In (Login)
- **Method**: `POST`
- **Endpoint**: `/auth/signin`
- **Access**: Public
- **Description**: Authenticates user via email or mobile number with password.
- **Request Payload**:
```json
{
  "identifier": "hardik@example.com",
  "password": "StrongPassword123!",
  "rememberMe": true
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 86400,
    "user": {
      "id": "usr_948214",
      "fullName": "Hardik Patel",
      "email": "hardik@example.com",
      "mobile": "+919876543210",
      "role": "organizer",
      "profilePhoto": "https://cdn.cricauction.app/avatars/usr_948214.jpg",
      "isVerified": true
    }
  }
}
```

#### 3. Send / Resend OTP
- **Method**: `POST`
- **Endpoint**: `/auth/otp/send`
- **Access**: Public
- **Description**: Dispatches a 6-digit OTP to mobile or email for verification or password recovery.
- **Request Payload**:
```json
{
  "mobile": "+919876543210",
  "purpose": "signup_verification"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "OTP sent successfully to +91 98765 43210",
  "data": {
    "retryAfterSeconds": 45,
    "otpExpiresInSeconds": 300
  }
}
```

#### 4. Verify OTP
- **Method**: `POST`
- **Endpoint**: `/auth/otp/verify`
- **Access**: Public
- **Description**: Verifies the 6-digit OTP code submitted on the `/verify` screen.
- **Request Payload**:
```json
{
  "mobile": "+919876543210",
  "otp": "482910",
  "purpose": "signup_verification"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Verification successful",
  "data": {
    "isVerified": true,
    "resetToken": "rst_9812480124",
    "accessToken": "eyJhbGciOi..."
  }
}
```

#### 5. Forgot Password
- **Method**: `POST`
- **Endpoint**: `/auth/password/forgot`
- **Access**: Public
- **Description**: Triggers password reset OTP to registered email or mobile.
- **Request Payload**:
```json
{
  "identifier": "hardik@example.com"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Password reset OTP dispatched",
  "data": {
    "maskedContact": "+91 98765 *****",
    "deliveryMethod": "SMS"
  }
}
```

#### 6. Reset Password
- **Method**: `POST`
- **Endpoint**: `/auth/password/reset`
- **Access**: Public (Requires Reset Token from OTP verification)
- **Description**: Sets the new password after OTP verification.
- **Request Payload**:
```json
{
  "resetToken": "rst_9812480124",
  "newPassword": "NewStrongPassword456!",
  "confirmPassword": "NewStrongPassword456!"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Password updated successfully. Please log in."
}
```

#### 7. Refresh Access Token
- **Method**: `POST`
- **Endpoint**: `/auth/refresh-token`
- **Access**: Public
- **Request Payload**:
```json
{
  "refreshToken": "eyJhbGciOi..."
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "expiresIn": 86400
  }
}
```

#### 8. User Logout
- **Method**: `POST`
- **Endpoint**: `/auth/logout`
- **Access**: Authenticated
- **Request Payload**:
```json
{
  "refreshToken": "eyJhbGciOi..."
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

#### 9. Get Current User Profile
- **Method**: `GET`
- **Endpoint**: `/auth/me`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "usr_948214",
    "fullName": "Hardik Patel",
    "email": "hardik@example.com",
    "mobile": "+919876543210",
    "role": "organizer",
    "city": "Surat",
    "profilePhoto": "https://cdn.cricauction.app/avatars/usr_948214.jpg",
    "isVerified": true
  }
}
```

#### 10. Switch / Set Active Role
- **Method**: `PATCH`
- **Endpoint**: `/auth/role`
- **Access**: Authenticated
- **Description**: Sets user role (`organizer`, `captain`, `player`) selected in `/role`.
- **Request Payload**:
```json
{
  "role": "organizer"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Role switched to organizer",
  "data": {
    "role": "organizer"
  }
}
```

---

### Module 2: Profile & Cricket Profile Management (4 APIs)

#### 11. Get User Profile Details
- **Method**: `GET`
- **Endpoint**: `/users/profile`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "fullName": "Hardik Patel",
    "email": "hardik@example.com",
    "mobile": "+919876543210",
    "city": "Surat, Gujarat",
    "profilePhoto": "https://cdn.cricauction.app/avatars/usr_948214.jpg",
    "role": "organizer",
    "bio": "Local cricket enthusiast and tournament manager."
  }
}
```

#### 12. Update User Profile
- **Method**: `PATCH`
- **Endpoint**: `/users/profile`
- **Access**: Authenticated
- **Request Payload**:
```json
{
  "fullName": "Hardik Patel",
  "mobile": "+919876543210",
  "city": "Surat, Gujarat"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Profile updated",
  "data": {
    "fullName": "Hardik Patel",
    "city": "Surat, Gujarat"
  }
}
```

#### 13. Upload Profile Photo
- **Method**: `POST`
- **Endpoint**: `/users/profile/photo`
- **Access**: Authenticated
- **Headers**: `Content-Type: multipart/form-data`
- **Form Data**: `photo: <binary file>`
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Photo uploaded successfully",
  "data": {
    "profilePhotoUrl": "https://cdn.cricauction.app/avatars/usr_948214_v2.jpg"
  }
}
```

#### 14. Update Cricket Player Bio & Stats
- **Method**: `PUT`
- **Endpoint**: `/users/player-profile`
- **Access**: Authenticated (Player Role)
- **Request Payload**:
```json
{
  "playingRole": "All-Rounder",
  "battingStyle": "Right-hand bat",
  "bowlingStyle": "Right-arm medium fast",
  "jerseyNumber": 18,
  "cricHeroesProfileUrl": "https://cricheroes.in/player-profile/12345",
  "pastTournaments": 12,
  "highestScore": 94,
  "bestBowling": "4/18"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Player cricket profile updated",
  "data": { "updatedAt": "2026-10-24T14:00:00Z" }
}
```

---

### Module 3: Role-Specific Dashboards (3 APIs)

#### 15. Organizer Dashboard Feed
- **Method**: `GET`
- **Endpoint**: `/dashboard/organizer`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "hasActiveDraft": true,
    "draftSummary": {
      "id": "tourn_spl_2026",
      "name": "Surat Premier League",
      "step": 3
    },
    "stats": {
      "myTournaments": 3,
      "teams": 10,
      "playersRegistered": 128,
      "upcomingAuctionDate": "28 Aug"
    },
    "upcomingTournament": {
      "id": "tourn_spl_2026",
      "name": "Surat Premier League",
      "status": "Registration Open",
      "teamsCount": 10,
      "playersCount": 128,
      "auctionDate": "2026-08-28T19:00:00Z",
      "auctionFormatted": "28 Aug • 7:00 PM"
    },
    "recentActivity": [
      {
        "id": "act_1",
        "type": "registration",
        "title": "128 players registered",
        "subtitle": "Surat Premier League",
        "timestamp": "2026-08-24T10:15:00Z"
      },
      {
        "id": "act_2",
        "type": "captain_joined",
        "title": "Captain joined your tournament",
        "subtitle": "Surat Strikers (Hardik Patel)",
        "timestamp": "2026-08-24T09:30:00Z"
      },
      {
        "id": "act_3",
        "type": "auction_scheduled",
        "title": "Auction scheduled for 28 Aug",
        "subtitle": "7:00 PM • Live auction room",
        "timestamp": "2026-08-23T18:00:00Z"
      }
    ]
  }
}
```

#### 16. Team Captain Dashboard Feed
- **Method**: `GET`
- **Endpoint**: `/dashboard/captain`
- **Access**: Captain
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "team": {
      "id": "team_surat_strikers",
      "name": "Surat Strikers",
      "tournamentId": "tourn_spl_2026",
      "tournamentName": "Surat Premier League",
      "totalBudget": 1000000,
      "remainingBudget": 850000,
      "budgetFormatted": "₹8.5L",
      "squadSize": 11,
      "maxSquad": 15
    },
    "upcomingAuction": {
      "tournamentId": "tourn_spl_2026",
      "name": "Surat Premier League",
      "auctionDate": "2026-08-28T19:00:00Z",
      "displayDate": "28 Aug • 7:00 PM",
      "isLive": false
    },
    "upcomingMatch": {
      "id": "match_01",
      "opponent": "City Warriors",
      "schedule": "Sunday • 4:00 PM",
      "ground": "Lalbhai Contractor Stadium"
    }
  }
}
```

#### 17. Player Dashboard Feed
- **Method**: `GET`
- **Endpoint**: `/dashboard/player`
- **Access**: Player
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "activeJourney": {
      "tournamentId": "tourn_spl_2026",
      "tournamentName": "Surat Premier League",
      "status": "Registered",
      "auctionAt": "28 Aug • 7:00 PM",
      "team": null,
      "basePrice": 5000
    },
    "myTournaments": [
      {
        "tournamentId": "tourn_spl_2026",
        "name": "Surat Premier League",
        "status": "Registered",
        "auctionDate": "28 Aug",
        "badge": "Active"
      },
      {
        "tournamentId": "tourn_dcc_2026",
        "name": "Diamond City Cup",
        "status": "Registration submitted",
        "badge": "Pending"
      }
    ],
    "upcomingMatches": []
  }
}
```

---

### Module 4: Tournament Management (Organizer) (8 APIs)

#### 18. Create Tournament Draft (Screen 16)
- **Method**: `POST`
- **Endpoint**: `/tournaments`
- **Access**: Organizer
- **Description**: Stores basic tournament details, location, venue, auction schedule, format, and dates.
- **Request Payload**:
```json
{
  "name": "Surat Premier League 2026",
  "location": "Surat, Gujarat",
  "ground": "Lalbhai Contractor Stadium",
  "startDate": "2026-09-05",
  "endDate": "2026-09-15",
  "auctionDate": "2026-08-28",
  "auctionTime": "19:00",
  "registrationStartDate": "2026-08-01",
  "registrationEndDate": "2026-08-25",
  "ballType": "Tennis",
  "format": "T20",
  "entryFee": "Free",
  "description": "Surat Premier League annual tournament and live auction."
}
```
- **Response (201 Created)**:
```json
{
  "success": true,
  "message": "Tournament draft created",
  "data": {
    "id": "tourn_spl_2026",
    "slug": "surat-premier-league-2026",
    "step": 1,
    "createdAt": "2026-10-24T14:00:00Z"
  }
}
```

#### 19. List Organizer Tournaments
- **Method**: `GET`
- **Endpoint**: `/tournaments`
- **Access**: Organizer
- **Query Params**: `status=draft|upcoming|ongoing|completed`
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "tourn_spl_2026",
      "name": "Surat Premier League 2026",
      "location": "Surat, Gujarat",
      "startDate": "2026-09-05",
      "endDate": "2026-09-15",
      "teamsCount": 10,
      "playersRegistered": 128,
      "status": "upcoming"
    }
  ]
}
```

#### 20. Get Tournament Details by ID
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "tourn_spl_2026",
    "name": "Surat Premier League 2026",
    "location": "Surat, Gujarat",
    "ground": "Lalbhai Contractor Stadium",
    "startDate": "2026-09-05",
    "endDate": "2026-09-15",
    "auctionDate": "2026-08-28",
    "auctionTime": "19:00",
    "auctionAt": "28 Aug • 7:00 PM",
    "ballType": "Tennis",
    "format": "T20",
    "entryFee": "Free",
    "settings": {
      "teams": 10,
      "squadMinimum": 12,
      "squadMaximum": 15,
      "budget": 1000000,
      "basePrice": 20000,
      "bidIncrement": 10000,
      "bidTimer": 10,
      "autoStartNext": false,
      "matches": 20,
      "matchDuration": 3
    }
  }
}
```

#### 21. Update Tournament Basic Details
- **Method**: `PATCH`
- **Endpoint**: `/tournaments/:id`
- **Access**: Organizer
- **Request Payload**: Same fields as creation (partial updates supported).
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Tournament details updated"
}
```

#### 22. Save Tournament Rules & Settings (Screen 17)
- **Method**: `PUT`
- **Endpoint**: `/tournaments/:id/settings`
- **Access**: Organizer
- **Description**: Configures squad boundaries, auction purses, bid timer, auto-start, and match parameters.
- **Request Payload**:
```json
{
  "teams": 10,
  "squadMinimum": 12,
  "squadMaximum": 15,
  "budget": 1000000,
  "basePrice": 20000,
  "bidIncrement": 10000,
  "bidTimer": 10,
  "autoStartNext": false,
  "matches": 20,
  "matchDuration": 3,
  "allowPlayersToWithdraw": true
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Tournament rules and auction settings saved",
  "data": {
    "step": 2,
    "teamsAllocated": 10
  }
}
```

#### 23. Publish / Launch Tournament
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/publish`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Tournament is now live for registrations and invitations",
  "data": {
    "status": "published",
    "registrationLink": "https://cricauction.app/register/surat-premier-league-2026-spl26",
    "captainInviteLink": "https://cricauction.app/join/surat-premier-league-2026-ca10-28aug"
  }
}
```

#### 24. Delete / Archive Tournament
- **Method**: `DELETE`
- **Endpoint**: `/tournaments/:id`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Tournament deleted successfully"
}
```

#### 25. Get Public Tournament Details (Share Link Lookup)
- **Method**: `GET`
- **Endpoint**: `/tournaments/public/:slug`
- **Access**: Public
- **Description**: Used by captain join link and player registration landing page.
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "tourn_spl_2026",
    "name": "Surat Premier League",
    "location": "Surat, Gujarat",
    "ground": "Lalbhai Contractor Stadium",
    "ballType": "Tennis",
    "format": "T20",
    "capacity": 150,
    "registered": 128,
    "registrationOpen": true,
    "auctionDate": "2026-08-28T19:00:00Z"
  }
}
```

---

### Module 5: Team & Captain Management (8 APIs)

#### 26. List Tournament Teams
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/teams`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "totalTeams": 10,
    "assignedCaptains": 4,
    "teams": [
      {
        "id": 1,
        "name": "Surat Strikers",
        "captainName": "Hardik Patel",
        "captainMobile": "+91 98765 43210",
        "status": "Accepted",
        "squadCount": 0
      },
      {
        "id": 5,
        "name": "Lions XI",
        "captainName": "Karan Mehta",
        "captainMobile": "+91 91234 56780",
        "status": "Invited",
        "squadCount": 0
      },
      {
        "id": 6,
        "name": "Rising Stars",
        "captainName": "",
        "captainMobile": "",
        "status": "Pending",
        "squadCount": 0
      }
    ]
  }
}
```

#### 27. Add New Team
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/teams`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "name": "Coastal Riders",
  "captainName": "Vijay Varma",
  "captainMobile": "+91 98221 12233"
}
```
- **Response (201 Created)**:
```json
{
  "success": true,
  "message": "Team added",
  "data": {
    "id": 7,
    "name": "Coastal Riders",
    "status": "Pending"
  }
}
```

#### 28. Edit Team & Captain Assignment
- **Method**: `PATCH`
- **Endpoint**: `/tournaments/:id/teams/:teamId`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "name": "Coastal Riders XI",
  "captainName": "Vijay Varma",
  "captainMobile": "+91 98221 12233"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Team details updated",
  "data": { "id": 7, "status": "Pending" }
}
```

#### 29. Delete Team
- **Method**: `DELETE`
- **Endpoint**: `/tournaments/:id/teams/:teamId`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Team removed from tournament"
}
```

#### 30. Send Captain Invitation (WhatsApp / SMS)
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/teams/:teamId/invite`
- **Access**: Organizer
- **Description**: Triggers invite message and marks team status as `Invited`.
- **Request Payload**:
```json
{
  "channel": "whatsapp"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Invite dispatched",
  "data": {
    "teamId": 5,
    "status": "Invited",
    "inviteLink": "https://cricauction.app/join/spl26-team5"
  }
}
```

#### 31. Captain Accepts Invite
- **Method**: `POST`
- **Endpoint**: `/teams/join`
- **Access**: Authenticated (User claiming Captain role)
- **Request Payload**:
```json
{
  "inviteCode": "spl26-team5",
  "captainMobile": "+91 91234 56780"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "You are now confirmed as Captain of Lions XI",
  "data": {
    "teamId": 5,
    "teamName": "Lions XI",
    "tournamentId": "tourn_spl_2026",
    "role": "captain"
  }
}
```

#### 32. Get Team Squad Roster & Purse
- **Method**: `GET`
- **Endpoint**: `/teams/:teamId/squad`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "teamId": 1,
    "teamName": "Surat Strikers",
    "totalPurse": 1000000,
    "spentPurse": 150000,
    "remainingPurse": 850000,
    "squadCount": 11,
    "maxSquad": 15,
    "players": [
      {
        "id": "ply_01",
        "name": "Rahul Patel",
        "role": "Batsman",
        "purchasePrice": 45000,
        "isCaptain": true
      }
    ]
  }
}
```

#### 33. Captain Updates Team Branding
- **Method**: `PATCH`
- **Endpoint**: `/teams/:teamId/profile`
- **Access**: Captain
- **Headers**: `Content-Type: multipart/form-data`
- **Form Data**: `logo: <binary file>, shortCode: "STR"`
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Team profile updated"
}
```

---

### Module 6: Player Registration & Capacity Management (7 APIs)

#### 34. Player Registers for Tournament
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/players/register`
- **Access**: Authenticated (Player Role)
- **Request Payload**:
```json
{
  "playingRole": "Batsman",
  "battingStyle": "Right-hand bat",
  "bowlingStyle": "Right-arm off spin",
  "basePrice": 5000,
  "city": "Surat",
  "mobile": "+91 98989 89898",
  "jerseySize": "L",
  "cricHeroesLink": "https://cricheroes.in/player/9912"
}
```
- **Response (201 Created)**:
```json
{
  "success": true,
  "message": "Registration submitted successfully",
  "data": {
    "registrationId": "reg_81920",
    "status": "Registered",
    "tournamentName": "Surat Premier League"
  }
}
```

#### 35. Player Registration Status Tracking
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/players/my-status`
- **Access**: Player
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "tournamentId": "tourn_spl_2026",
    "tournamentName": "Surat Premier League",
    "status": "Registered",
    "basePrice": 5000,
    "auctionSchedule": "2026-08-28T19:00:00Z",
    "soldToTeam": null,
    "soldPrice": null
  }
}
```

#### 36. Player Withdraws from Tournament
- **Method**: `DELETE`
- **Endpoint**: `/tournaments/:id/players/withdraw`
- **Access**: Player
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "You have successfully withdrawn from this tournament"
}
```

#### 37. Organizer Lists Registered Players
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/players`
- **Access**: Organizer
- **Query Params**: `filter=All|Registered|Pending|Batsman|Bowler|All-Rounder|Wicket Keeper&search=rahul&page=1&limit=20`
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "total": 128,
    "page": 1,
    "limit": 20,
    "players": [
      {
        "id": 1,
        "name": "Rahul Patel",
        "role": "Batsman",
        "basePrice": "₹5,000",
        "status": "Registered",
        "mobile": "+91 98765 11111",
        "photo": "https://cdn.cricauction.app/players/1.jpg"
      },
      {
        "id": 2,
        "name": "Akash Shah",
        "role": "All-Rounder",
        "basePrice": "₹7,500",
        "status": "Registered",
        "mobile": "+91 98765 22222",
        "photo": "https://cdn.cricauction.app/players/2.jpg"
      },
      {
        "id": 3,
        "name": "Vivek Joshi",
        "role": "Bowler",
        "basePrice": "₹5,000",
        "status": "Pending",
        "mobile": "+91 98765 33333",
        "photo": null
      }
    ]
  }
}
```

#### 38. Player Registration Capacity Stats
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/players/stats`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "capacity": 150,
    "registered": 128,
    "pending": 8,
    "approved": 120,
    "percentageFilled": 85,
    "isOpen": true,
    "roleBreakdown": {
      "batsmen": 45,
      "bowlers": 40,
      "allRounders": 30,
      "wicketKeepers": 13
    }
  }
}
```

#### 39. Organizer Approves / Rejects Player
- **Method**: `PATCH`
- **Endpoint**: `/tournaments/:id/players/:playerId/status`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "status": "Registered",
  "reason": "Documents verified"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Player status updated"
}
```

#### 40. Organizer Sets / Updates Player Base Price
- **Method**: `PATCH`
- **Endpoint**: `/tournaments/:id/players/:playerId/base-price`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "basePrice": 10000
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Player base price updated"
}
```

---

### Module 7: Live Auction Engine (REST + WebSocket) (13 REST APIs + 8 Events)

#### 41. Initialize & Open Auction Room
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/start`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Auction room is now LIVE",
  "data": {
    "auctionId": "auc_9102",
    "status": "LIVE",
    "startedAt": "2026-08-28T19:00:00Z"
  }
}
```

#### 42. Pause Live Auction
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/pause`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Auction paused"
}
```

#### 43. Resume Live Auction
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/resume`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Auction resumed"
}
```

#### 44. Call Next Player to Hammer
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/next-player`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "playerId": "ply_01" // Optional: Omit for auto-queue
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "player": {
      "id": "ply_01",
      "name": "Rahul Patel",
      "role": "Batsman",
      "battingStyle": "Right-hand",
      "basePrice": 5000,
      "photo": "https://cdn.cricauction.app/players/1.jpg"
    },
    "currentBid": 5000,
    "leadingTeam": null,
    "timerSeconds": 10
  }
}
```

#### 45. Place Bid (Captain)
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/bid`
- **Access**: Captain
- **Request Payload**:
```json
{
  "bidAmount": 15000,
  "teamId": 1
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Bid accepted",
  "data": {
    "currentBid": 15000,
    "leadingTeamId": 1,
    "leadingTeamName": "Surat Strikers",
    "timerReset": 10
  }
}
```

#### 46. Sell Player to Leading Team ("Sold")
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/sell`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Player SOLD",
  "data": {
    "playerId": "ply_01",
    "playerName": "Rahul Patel",
    "soldToTeamId": 1,
    "soldToTeamName": "Surat Strikers",
    "finalPrice": 45000,
    "remainingPurse": 805000
  }
}
```

#### 47. Pass Player as Unsold
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/pass`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Player marked UNSOLD",
  "data": {
    "playerId": "ply_03",
    "status": "UNSOLD"
  }
}
```

#### 48. Undo Last Bid
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/undo`
- **Access**: Organizer
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Last bid reverted",
  "data": {
    "currentBid": 35000,
    "leadingTeamId": 2,
    "leadingTeamName": "City Warriors"
  }
}
```

#### 49. Get Current Auction Room State (Full Snapshot)
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/auction/state`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "status": "LIVE",
    "currentPlayer": {
      "id": "ply_01",
      "name": "Rahul Patel",
      "role": "Batsman",
      "basePrice": 5000,
      "currentBid": 35000,
      "leadingTeam": "Surat Strikers",
      "secondsRemaining": 7
    },
    "teams": [
      {
        "id": 1,
        "name": "Surat Strikers",
        "remainingBudget": 850000,
        "squadCount": 11,
        "maxSquad": 15
      }
    ],
    "upcomingQueueCount": 24,
    "soldCount": 42,
    "unsoldCount": 6
  }
}
```

#### 50. Get Auction Transaction & Sold History
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/auction/history`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "round": 1,
      "playerId": "ply_10",
      "playerName": "Hardik Shah",
      "role": "All-Rounder",
      "soldTo": "Surat Strikers",
      "price": 85000,
      "status": "SOLD"
    }
  ]
}
```

#### 51. Start Accelerated / Unsold Re-entry Round
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/accelerated-round`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "includeUnsold": true,
  "revisedBasePriceFactor": 0.8
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Accelerated round prepared with 8 unsold players"
}
```

#### 52. Captain Views Target Player Wishlist
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/auction/wishlist`
- **Access**: Captain
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    { "playerId": "ply_04", "priority": "High", "maxBudget": 75000 }
  ]
}
```

#### 53. Captain Adds / Removes Player from Wishlist
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/auction/wishlist`
- **Access**: Captain
- **Request Payload**:
```json
{
  "playerId": "ply_04",
  "action": "add",
  "maxBudget": 75000
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Wishlist updated"
}
```

---

### Real-Time WebSocket Events Specification

Connected via: `wss://api.cricauction.app/ws/v1/auction?token=<JWT>&tournamentId=<ID>`

```mermaid
sequenceDiagram
    autonumber
    actor Org as Organizer
    actor Cap as Captain
    participant WS as WebSocket Gateway
    actor Ply as Player / Spectator

    Cap->>WS: CONNECT (Bearer Token)
    WS-->>Cap: 101 Switching Protocols
    Org->>WS: SEND { "action": "NEXT_PLAYER", "playerId": "ply_01" }
    WS->>Cap: BROADCAST "AUCTION_NEW_PLAYER"
    WS->>Ply: BROADCAST "AUCTION_NEW_PLAYER"

    Cap->>WS: SEND { "action": "PLACE_BID", "bid": 20000 }
    WS->>WS: Validate purse & minimum increment
    WS->>Cap: BROADCAST "AUCTION_BID_PLACED"
    WS->>Ply: BROADCAST "AUCTION_BID_PLACED"
    WS->>Org: BROADCAST "AUCTION_BID_PLACED"

    Note over WS: Timer ticks: 10, 9, 8...
    WS->>Cap: BROADCAST "AUCTION_TIMER_TICK" { "seconds": 3 }

    Org->>WS: SEND { "action": "HAMMER_SOLD" }
    WS->>Cap: BROADCAST "AUCTION_PLAYER_SOLD"
    WS->>Ply: BROADCAST "AUCTION_PLAYER_SOLD"
```

| Event Name | Direction | Payload Key Fields |
| :--- | :--- | :--- |
| `AUCTION_ROOM_STATE` | Server -> Client | Full snapshot upon joining |
| `AUCTION_NEW_PLAYER` | Server -> Client | `playerId`, `name`, `role`, `basePrice`, `timer` |
| `AUCTION_BID_PLACED` | Server -> Client | `bidAmount`, `teamId`, `teamName`, `nextAllowedBid` |
| `AUCTION_TIMER_TICK` | Server -> Client | `secondsRemaining`, `status` |
| `AUCTION_GOING_TWICE` | Server -> Client | `warningLevel` (1 = Going Once, 2 = Going Twice) |
| `AUCTION_PLAYER_SOLD` | Server -> Client | `playerId`, `teamId`, `amount`, `newTeamPurse` |
| `AUCTION_PLAYER_UNSOLD`| Server -> Client | `playerId`, `status: UNSOLD` |
| `AUCTION_PAUSED_RESUMED`| Server -> Client | `status: PAUSED | LIVE` |

---

### Module 8: Matches, Fixtures & Scoring (5 APIs)

#### 54. List Tournament Fixtures
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/matches`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "match_01",
      "team1": "Surat Strikers",
      "team2": "City Warriors",
      "scheduledAt": "2026-09-06T16:00:00Z",
      "ground": "Lalbhai Contractor Stadium",
      "status": "UPCOMING"
    }
  ]
}
```

#### 55. Create Match Fixture
- **Method**: `POST`
- **Endpoint**: `/tournaments/:id/matches`
- **Access**: Organizer
- **Request Payload**:
```json
{
  "team1Id": 1,
  "team2Id": 2,
  "scheduledAt": "2026-09-06T16:00:00Z",
  "ground": "Lalbhai Contractor Stadium",
  "overs": 20
}
```
- **Response (201 Created)**:
```json
{
  "success": true,
  "message": "Fixture scheduled"
}
```

#### 56. Get Match Scorecard
- **Method**: `GET`
- **Endpoint**: `/matches/:matchId`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "matchId": "match_01",
    "team1": { "name": "Surat Strikers", "runs": 164, "wickets": 4, "overs": 20 },
    "team2": { "name": "City Warriors", "runs": 120, "wickets": 7, "overs": 16.2 },
    "currentBatsmen": [{ "name": "Rahul Patel", "runs": 48, "balls": 29 }],
    "currentBowler": { "name": "Vivek Joshi", "overs": 3.2, "wickets": 2 }
  }
}
```

#### 57. Live Match Ball-by-Ball Update
- **Method**: `PATCH`
- **Endpoint**: `/matches/:matchId/score`
- **Access**: Organizer (Scorer)
- **Request Payload**:
```json
{
  "over": 16,
  "ball": 3,
  "runs": 4,
  "isWicket": false,
  "batsmanId": "ply_01",
  "bowlerId": "ply_03"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Ball recorded"
}
```

#### 58. Get Tournament Standings (Points Table)
- **Method**: `GET`
- **Endpoint**: `/tournaments/:id/standings`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "rank": 1,
      "team": "Surat Strikers",
      "played": 3,
      "won": 3,
      "lost": 0,
      "nrr": "+1.420",
      "points": 6
    }
  ]
}
```

---

### Module 9: Notifications & Alerts (4 APIs)

#### 59. List Notifications
- **Method**: `GET`
- **Endpoint**: `/notifications`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "notif_01",
      "title": "Auction Starting Soon",
      "body": "Surat Premier League auction starts in 1 hour.",
      "read": false,
      "createdAt": "2026-08-28T18:00:00Z"
    }
  ]
}
```

#### 60. Mark Notification as Read
- **Method**: `PATCH`
- **Endpoint**: `/notifications/:id/read`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{ "success": true }
```

#### 61. Mark All Notifications as Read
- **Method**: `POST`
- **Endpoint**: `/notifications/read-all`
- **Access**: Authenticated
- **Response (200 OK)**:
```json
{ "success": true }
```

#### 62. Register Device Push Token (FCM / APNS)
- **Method**: `POST`
- **Endpoint**: `/notifications/device-token`
- **Access**: Authenticated
- **Request Payload**:
```json
{
  "deviceToken": "fcm_token_string_here",
  "platform": "ios"
}
```
- **Response (200 OK)**:
```json
{ "success": true }
```

---

## 4. Summary Matrix: API Count by Module & Role

| Module No. | Module Name | Number of Endpoints | Primary Target Role |
| :---: | :--- | :---: | :--- |
| **1** | Authentication & OTP Verification | **10** | Public & All Roles |
| **2** | User Profile & Cricket Bio | **4** | All Roles / Players |
| **3** | Role Dashboards Feed | **3** | Organizer, Captain, Player |
| **4** | Tournament Creation & Rules | **8** | Tournament Organizer |
| **5** | Teams & Captain Management | **8** | Organizer & Team Captain |
| **6** | Player Registrations & Capacity | **7** | Organizer & Player |
| **7** | Live Auction Engine (REST) | **13** | Organizer & Captain |
| **8** | Fixtures, Scoring & Standings | **5** | Organizer, Captain, Player |
| **9** | Notifications & Push Messaging | **4** | All Roles |
| **Total** | **Full System REST Endpoints** | **62 APIs** | *(+ 8 WebSocket Events)* |

---

## 5. Comprehensive Module-Based API Categorization, Functional Uses & Execution Roadmap

This section maps all **62 REST Endpoints (+ 8 WebSocket Events)** across domain modules, provides their exact real-world functional use cases within the CricAuction mobile/web apps, and lays out the architectural dependency order to decide which module to develop next.

---

### Module 1: Authentication & Authorization (10 APIs)
*Domain Scope: User identity, omni-channel sign-in/up, OTP verification, JWT session tokens, dynamic role switching.*  
*Status: **COMPLETED & OPTIMIZED** (Verified under 5,000 concurrent user benchmark)*

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **1** | `POST /auth/signup` | Public | **New User Registration Screen**: Mobile/Web user inputs full name, email, mobile, and password. Dispatches verification OTP. | `users`, `otp_verifications` | Completed |
| **2** | `POST /auth/signin` | Public | **Credential Login Screen**: Returning user logs in with email or mobile + password. Returns JWT access token & refresh token. | `users`, `refresh_tokens` | Completed |
| **3** | `POST /auth/otp/send` | Public | **OTP Dispatch Trigger**: Triggered when requesting SMS/Email OTP for login, phone verification, or password recovery. Enforces 45s cooldown. | `otp_verifications` | Completed |
| **4** | `POST /auth/otp/verify` | Public | **OTP Input Screen**: Verifies 6-digit verification code. If valid, confirms user contact or yields single-use password reset token. | `otp_verifications` | Completed |
| **5** | `POST /auth/otp/login` | Public | **Passwordless Login Screen**: User enters mobile or email + received OTP. Completes login and auto-provisions new user accounts seamlessly. | `users`, `otp_verifications`, `refresh_tokens` | Completed |
| **6** | `POST /auth/google` | Public | **Google One-Tap / SDK Login**: Direct OAuth2 sign-in/sign-up. Verifies Google ID token, links existing accounts or provisions new users. | `users`, `refresh_tokens` | Completed |
| **7** | `POST /auth/password/forgot` | Public | **Forgot Password Screen**: User requests password recovery. Dispatches OTP to registered mobile/email. | `users`, `otp_verifications` | Completed |
| **8** | `POST /auth/password/reset` | Public | **Set New Password Screen**: Consumes verified reset token and securely updates user password hash (BCrypt). | `users`, `password_reset_tokens` | Completed |
| **9** | `POST /auth/refresh-token` | Public | **Silent Background Refresh**: Mobile app refreshes expired access token (24h) without logging the user out. | `refresh_tokens` | Completed |
| **10** | `POST /auth/logout` | Authenticated | **User Logout Trigger**: Revokes refresh token in PostgreSQL and flushes active client session. | `refresh_tokens` | Completed |

---

### Module 2: Profile & Cricket Profile Management (4 APIs)
*Domain Scope: User personal profile, avatar uploads to S3/CDN, and specialized cricket statistics (CricHeroes sync, batting/bowling styles).*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **11** | `GET /users/profile` | Authenticated | **My Profile Screen**: Retrieves personal profile details (full name, email, mobile, city, bio, avatar, and active role). | `users`, `player_profiles` | Planned |
| **12** | `PATCH /users/profile` | Authenticated | **Edit Profile Screen**: Allows updating user name, city, and secondary contact details. | `users` | Planned |
| **13** | `POST /users/profile/photo` | Authenticated | **Profile Avatar Upload**: Multipart upload of profile photo. Generates CDN/S3 URL and updates user profile. | `users` | Planned |
| **14** | `PUT /users/player-profile` | Player | **Cricket Bio & Stats Setup (Screen 14)**: Sets player playing role (Batsman, Bowler, All-Rounder, Wicket Keeper), batting/bowling style, jersey number, CricHeroes profile link, and career stats. | `player_profiles` | Planned |

---

### Module 3: Role-Specific Dashboards (3 APIs)
*Domain Scope: Aggregated home screens tailored to the active role (Organizer, Captain, Player) with metrics, live auction alerts, and quick actions.*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **15** | `GET /dashboard/organizer` | Organizer | **Organizer Home Screen**: Returns active tournament drafts, registration counts, team invitation status, and upcoming auction timers. | `tournaments`, `teams`, `tournament_players` | Planned |
| **16** | `GET /dashboard/captain` | Captain | **Captain Home Screen**: Displays team purse budget, squad fill count (e.g. 11/15 players), upcoming auction countdown, and upcoming match schedule. | `teams`, `squad_members`, `tournaments`, `matches` | Planned |
| **17** | `GET /dashboard/player` | Player | **Player Home Screen**: Shows active tournament registration status (Registered, Sold, Unsold), auction lot number, target squad placement, and match schedule. | `tournament_players`, `squad_members`, `tournaments` | Planned |

---

### Module 4: Tournament Creation & Rules Management (8 APIs)
*Domain Scope: Tournament lifecycle (Draft -> Published -> Registration Open -> Live -> Completed), rules, purse budgets, bid timers, and public share links.*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **18** | `POST /tournaments` | Organizer | **Tournament Wizard Step 1 (Screen 16)**: Organizer creates tournament draft with name, ground venue, dates, ball type (Tennis/Leather), format (T20/T10), and entry fee. | `tournaments` | Planned |
| **19** | `GET /tournaments` | Organizer | **My Tournaments List**: Returns list of all tournaments created by organizer, filterable by status (`draft`, `upcoming`, `ongoing`, `completed`). | `tournaments` | Planned |
| **20** | `GET /tournaments/:id` | Authenticated | **Tournament Detail Screen**: Retrieves complete tournament information, schedule, venue, and configured auction rules. | `tournaments`, `tournament_settings` | Planned |
| **21** | `PATCH /tournaments/:id` | Organizer | **Edit Tournament Details**: Updates tournament dates, venue ground, banner image, or description. | `tournaments` | Planned |
| **22** | `PUT /tournaments/:id/settings` | Organizer | **Auction Rules Setup (Screen 17)**: Configures total teams, squad min/max limits, team purse budget (e.g. 10 Lakhs), default base price, bid increments, and bid countdown timer. | `tournament_settings` | Planned |
| **23** | `POST /tournaments/:id/publish` | Organizer | **Launch Tournament**: Finalizes setup and publishes tournament. Generates public player registration link and captain invite links. | `tournaments` | Planned |
| **24** | `DELETE /tournaments/:id` | Organizer | **Delete / Archive Tournament**: Cancels or archives draft/upcoming tournament. | `tournaments` | Planned |
| **25** | `GET /tournaments/public/:slug` | Public | **Public Landing Page (Share Link)**: Publicly viewable tournament overview for unregistered players and invited captains before logging in. | `tournaments`, `tournament_settings` | Planned |

---

### Module 5: Teams & Captain Management (8 APIs)
*Domain Scope: Team creation, captain assignments, invitation dispatch via WhatsApp/SMS, captain acceptance, team purse tracking, and team branding.*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **26** | `GET /tournaments/:id/teams` | Authenticated | **Tournament Teams List (Screen 18)**: Displays all teams participating in tournament, assigned captains, status (Pending, Invited, Accepted), and squad sizes. | `teams`, `users` | Planned |
| **27** | `POST /tournaments/:id/teams` | Organizer | **Add Team Modal (Screen 19)**: Adds team name and placeholder captain contact info. Allocates tournament purse budget. | `teams` | Planned |
| **28** | `PATCH /tournaments/:id/teams/:teamId` | Organizer | **Edit Team Assignment**: Updates team details or re-assigns placeholder captain name/mobile. | `teams` | Planned |
| **29** | `DELETE /tournaments/:id/teams/:teamId` | Organizer | **Remove Team**: Removes an uncommitted team from the tournament roster. | `teams` | Planned |
| **30** | `POST /tournaments/:id/teams/:teamId/invite` | Organizer | **Send Captain Invite**: Dispatches invitation link via WhatsApp/SMS to captain candidate with unique claim code. | `teams` | Planned |
| **31** | `POST /teams/join` | Captain | **Captain Claim Link Screen**: Captain opens invite link, confirms mobile number, and claims ownership of team and captaincy. | `teams`, `users` | Planned |
| **32** | `GET /teams/:teamId/squad` | Authenticated | **Team Squad & Purse Roster**: Displays bought players, purchase prices, remaining purse budget, and remaining squad capacity. | `teams`, `squad_members`, `tournament_players` | Planned |
| **33** | `PATCH /teams/:teamId/profile` | Captain | **Team Branding Screen**: Captain uploads team logo and sets short code (e.g. "STR" for Surat Strikers). | `teams` | Planned |

---

### Module 6: Player Registration & Capacity Management (7 APIs)
*Domain Scope: Player tournament enrollment, vetting/approval workflows, base price setting, capacity enforcement, and withdrawal.*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **34** | `POST /tournaments/:id/players/register` | Player | **Player Registration Screen (Screen 20)**: Player submits enrollment for a tournament with chosen role, jersey size, base price, and CricHeroes link. | `tournament_players`, `player_profiles` | Planned |
| **35** | `GET /tournaments/:id/players/my-status` | Player | **Registration Tracker Screen**: Player checks if registration is Pending, Approved, or Rejected, and views assigned auction lot number. | `tournament_players` | Planned |
| **36** | `DELETE /tournaments/:id/players/withdraw` | Player | **Withdraw Registration Action**: Player opts out of tournament prior to auction start. | `tournament_players` | Planned |
| **37** | `GET /tournaments/:id/players` | Organizer | **Organizer Player Approval Screen**: Paginated list of registered players with role filters, status filters, and search capabilities. | `tournament_players`, `users` | Planned |
| **38** | `GET /tournaments/:id/players/stats` | Organizer | **Capacity Dashboard Widget**: Capacity meter (e.g. 128/150 filled), approval count, and role distribution breakdown. | `tournament_players`, `tournament_settings` | Planned |
| **39** | `PATCH /tournaments/:id/players/:playerId/status` | Organizer | **Approve / Reject Action**: Organizer marks applicant as Approved or Rejected with reason. | `tournament_players` | Planned |
| **40** | `PATCH /tournaments/:id/players/:playerId/base-price` | Organizer | **Base Price Adjustment**: Organizer reviews and sets official base auction price for player. | `tournament_players` | Planned |

---

### Module 7: Live Auction Engine (REST + WebSocket) (13 REST APIs + 8 Events)
*Domain Scope: Real-time live player auction room ("the hammer"), advisory lock bidding serialization, UNLOGGED fast state cache, and instant SOLD transactions.*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **41** | `POST /tournaments/:id/auction/start` | Organizer | **Start Auction Room**: Organizer opens live auction room, initializing the `live_auction_state` UNLOGGED table. | `tournaments`, `live_auction_state` | Planned |
| **42** | `POST /tournaments/:id/auction/pause` | Organizer | **Pause Auction Action**: Temporarily freezes the bid countdown timer during breaks or disputes. | `live_auction_state` | Planned |
| **43** | `POST /tournaments/:id/auction/resume` | Organizer | **Resume Auction Action**: Resumes bidding timer and action acceptance. | `live_auction_state` | Planned |
| **44** | `POST /tournaments/:id/auction/next-player` | Organizer | **Call Player to Hammer**: Loads next lot player onto the bidding block with base price and 10s countdown. | `live_auction_state`, `tournament_players` | Planned |
| **45** | `POST /tournaments/:id/auction/bid` | Captain | **Bid Button Tap**: Captain submits competitive bid. Serialized via PostgreSQL transaction advisory lock `pg_advisory_xact_lock`. | `live_auction_state`, `auction_bids`, `teams` | Planned |
| **46** | `POST /tournaments/:id/auction/sell` | Organizer | **Hammer Strike: "SOLD"**: Strikes hammer. Atomically transfers player to team squad, deducts purse budget, and clears auction block in one ACID transaction. | `squad_members`, `teams`, `tournament_players`, `live_auction_state` | Planned |
| **47** | `POST /tournaments/:id/auction/pass` | Organizer | **Hammer Strike: "UNSOLD"**: Passes player with no bids. Marks player status as `UNSOLD`. | `tournament_players`, `live_auction_state` | Planned |
| **48** | `POST /tournaments/:id/auction/undo` | Organizer | **Undo Misclick Bid**: Reverts erroneous last bid to the preceding bidder in the audit ledger. | `live_auction_state`, `auction_bids` | Planned |
| **49** | `GET /tournaments/:id/auction/state` | Authenticated | **Room Snapshot Initial Load**: Full state snapshot when opening the auction screen (current player, bid, leading team, timer, squad rosters). | `live_auction_state`, `teams` | Planned |
| **50** | `GET /tournaments/:id/auction/history` | Authenticated | **Sold / Unsold Log Screen**: Historical transaction ledger of all hammered players and prices. | `auction_bids`, `squad_members` | Planned |
| **51** | `POST /tournaments/:id/auction/accelerated-round` | Organizer | **Accelerated Re-Entry Round**: Re-queues unsold players for a speed round with adjusted base prices. | `tournament_players`, `live_auction_state` | Planned |
| **52** | `GET /tournaments/:id/auction/wishlist` | Captain | **Captain Strategy Wishlist**: Captain views target players, priority tier, and max intended budget. | `player_wishlists` | Planned |
| **53** | `POST /tournaments/:id/auction/wishlist` | Captain | **Add / Remove Wishlist Target**: Captain pins or unpins target player to wishlist. | `player_wishlists` | Planned |

#### Real-Time WebSocket Events (`/ws/v1/auction`):
1. `AUCTION_ROOM_STATE`: Full synchronization snapshot upon connection.
2. `AUCTION_NEW_PLAYER`: Player called to the auction hammer.
3. `AUCTION_BID_PLACED`: Real-time notification of new leading bid & bidder.
4. `AUCTION_TIMER_TICK`: 10-second countdown tick broadcast to all clients.
5. `AUCTION_GOING_TWICE`: Going once / Going twice warning.
6. `AUCTION_PLAYER_SOLD`: Player purchased celebration & purse deduction broadcast.
7. `AUCTION_PLAYER_UNSOLD`: Player passed without bids broadcast.
8. `AUCTION_PAUSED_RESUMED`: Live auction room paused / resumed notice.

---

### Module 8: Matches, Fixtures & Scoring (5 APIs)
*Domain Scope: Tournament fixture scheduling, ball-by-ball score tracking, live scorecard display, and tournament points table (NRR).*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **54** | `GET /tournaments/:id/matches` | Authenticated | **Fixtures Tab**: Retrieves complete tournament match schedule and status (`UPCOMING`, `LIVE`, `COMPLETED`). | `matches`, `teams` | Planned |
| **55** | `POST /tournaments/:id/matches` | Organizer | **Schedule Match Modal**: Organizer schedules fixture between Team 1 and Team 2 with date, time, venue, and overs. | `matches` | Planned |
| **56** | `GET /matches/:matchId` | Authenticated | **Live Scorecard Screen**: Real-time scorecard showing total runs, wickets, overs, current striker, and bowler. | `matches`, `match_scorecards` | Planned |
| **57** | `PATCH /matches/:matchId/score` | Organizer (Scorer) | **Scorer Ball Entry Screen**: Official scorer records each delivery (runs, extra, wicket, batsman, bowler). | `match_scorecards`, `matches` | Planned |
| **58** | `GET /tournaments/:id/standings` | Authenticated | **Points Table Screen**: Displays standings, matches played, won, lost, points, and Net Run Rate (NRR). | `matches`, `teams` | Planned |

---

### Module 9: Notifications & Push Alerts (4 APIs)
*Domain Scope: In-app notification inbox, read/unread states, and mobile push token registration (FCM/APNS).*  
*Status: **READY FOR DEVELOPMENT***

| # | HTTP Method & Route | Target Role | Functional Use Case & Real-World Trigger | Database Entities Impacted | Status |
|:---:|:---|:---:|:---|:---|:---:|
| **59** | `GET /notifications` | Authenticated | **Notification Center Screen**: Displays inbox of auction alerts, invite notices, and match schedules. | `notifications` | Planned |
| **60** | `PATCH /notifications/:id/read` | Authenticated | **Read Item Trigger**: Marks individual notification as read upon tap. | `notifications` | Planned |
| **61** | `POST /notifications/read-all` | Authenticated | **Mark All Read Action**: Clears unread badge counter in one tap. | `notifications` | Planned |
| **62** | `POST /notifications/device-token` | Authenticated | **Push Token Registration**: Mobile client registers FCM/APNS token on app startup for remote push notifications. | `device_push_tokens` | Planned |

---

## 6. Architectural Dependency Graph & Module Execution Order

The entities and business workflows exhibit a strict natural domain hierarchy:

```mermaid
flowchart TD
    M1["Module 1: Authentication & RBAC
(users, tokens, roles)
[COMPLETED]"] --> M2["Module 2: Profile & Cricket Bio
(player_profiles)"]
    M1 --> M4["Module 4: Tournament Setup & Rules
(tournaments, tournament_settings)"]

    M4 --> M5["Module 5: Team & Captain Management
(teams, captain_invites)"]
    M4 --> M6["Module 6: Player Registration
(tournament_players)"]
    M2 --> M6

    M5 & M6 --> M7["Module 7: Live Auction Engine
(live_auction_state, bids, squad_members)"]

    M5 & M4 --> M8["Module 8: Matches & Scoring
(matches, match_scorecards)"]

    M1 & M4 & M7 --> M9["Module 9: Notifications & Push
(notifications, device_tokens)"]

    M4 & M5 & M6 & M7 --> M3["Module 3: Role Dashboards Feed
(Aggregates M4, M5, M6, M7)"]
```

### Dependency Rules:
1. **Module 1 (Auth)** is complete and provides the security identity context (`UserPrincipal`) for all downstream modules.
2. **Module 4 (Tournament Management)** is the **root operational backbone** of the entire system:
   - Teams (Module 5) cannot exist without a Tournament.
   - Player Registrations (Module 6) cannot occur without a Tournament.
   - The Live Auction (Module 7) cannot run without Teams, Players, and a Tournament.
   - Matches (Module 8) cannot be scheduled without Teams and a Tournament.
3. **Module 2 (User & Cricket Profile)** extends the `User` entity with player bio, CricHeroes link, and batting/bowling statistics.
4. **Module 3 (Dashboards)** is an **aggregation layer**; building it after the core entities (Tournaments, Teams, Registrations) ensures its feeds have real underlying data to query.

---

## 7. Recommended Next Module to Pick Up

Based on domain dependencies and business value, there are two logical choices for the next development cycle:

### Option 1 (Recommended): Module 4 - Tournament Creation & Rules Management (8 APIs)
- **Why**: It is the foundational anchor for all other tournament-centric modules (Teams, Player Registrations, Auction Room, Matches). Once Tournament APIs exist, all other features can be plugged into real tournament instances.
- **Scope**:
  - Flyway migration `V4__create_tournaments_and_settings_schema.sql` (`tournaments`, `tournament_settings`).
  - DTOs, Repository, Service contracts, and REST Controller for APIs 18–25.
  - Slug generation, date validation, and public link resolution.

### Option 2: Module 2 - User Profile & Cricket Bio Management (4 APIs)
- **Why**: Direct leaf extension of the existing `users` table. Fast and self-contained.
- **Scope**:
  - Flyway migration for `player_profiles` table.
  - S3 / Local multipart photo upload handler.
  - Cricket bio update APIs 11–14.

> **Recommendation**: Implement **Module 4 (Tournament Management)** first to establish the tournament backbone, followed immediately by Module 5 (Teams) or Module 2 (Profiles).
