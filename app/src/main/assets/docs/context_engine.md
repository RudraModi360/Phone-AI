# Context Processing Engine

The Context Engine handles the compilation and dynamic injection of real-time environmental context and user profiles into the Logy system prompt.

## Components of Context

1. **Static System Prompt**: Predefined model boundaries, safety guardrails, and tool specifications.
2. **Dynamic User Context**:
   - **About the User**: Profile attributes (`profileName` and `profileRole`).
   - **User Preferences**: Context and behavior constraints curated by the `PreferenceProcessor`.
   - **Current Environmental Context**: Hardware/OS and situational data extracted dynamically.

## Environmental Context Parameters

- **Time**: System UTC/Local time.
- **Timezone**: Active timezone identifier (e.g., `America/New_York`).
- **Device Model**: Manufacturer and product specifications (e.g., `Google Pixel 8 Pro`).
- **OS Version**: Android Release Version (e.g., `Android 14`).
- **Battery Status**: Live battery capacity percentage.
