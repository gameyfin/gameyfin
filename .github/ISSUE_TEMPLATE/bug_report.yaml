name: Bug report
description: Create a report to help us improve
title: ''
labels:
 - Bug
assignees:
  - grimsi

body:
  - type: markdown
    attributes:
      value: |
        "## Before submitting your bug report"
        
        To help us resolve your issue efficiently, please ensure you have reviewed our [FAQs](https://gameyfin.org/faq/) and [Getting started guide](https://gameyfin.org/installation/getting-started/). 
        
        **Issues that could have been resolved by following these resources may be closed to allow us to focus on genuine bugs.**

  - type: checkboxes
    id: prerequisites
    attributes:
      label: Prerequisites
      description: Please confirm you have read and understood the following resources
      options:
        - label: I have read and understood the [FAQs](https://gameyfin.org/faq/)
          required: true
        - label: I have read and understood the [Getting started guide](https://gameyfin.org/installation/getting-started/)
          required: true

  - type: textarea
    id: description
    attributes:
      label: Bug Description
      description: A clear and concise description of what the bug is
      placeholder: Describe the bug...
    validations:
      required: true

  - type: input
    id: version
    attributes:
      label: Gameyfin Version
      description: What version of Gameyfin are you running?
      placeholder: e.g. v2.0.0.beta3
    validations:
      required: true

  - type: dropdown
    id: installation-type
    attributes:
      label: Installation Type
      description: How did you install Gameyfin?
      options:
        - Docker
        - Unraid
        - Other (please specify in Additional Context)
    validations:
      required: true

  - type: input
    id: browser
    attributes:
      label: Browser with Version
      description: Which browser are you using?
      placeholder: e.g. Chrome 120.0.6099.129, Firefox 121.0, Safari 17.2
    validations:
      required: true

  - type: textarea
    id: reproduction
    attributes:
      label: How to Reproduce
      description: Steps to reproduce the behavior
      placeholder: |
        1. Go to '...'
        2. Click on '....'
        3. Scroll down to '....'
        4. See error
    validations:
      required: true

  - type: textarea
    id: expected-behavior
    attributes:
      label: Expected Behavior
      description: A clear and concise description of what you expected to happen
      placeholder: What should have happened?
    validations:
      required: true

  - type: textarea
    id: actual-behavior
    attributes:
      label: Actual Behavior
      description: A clear and concise description of what actually happened
      placeholder: What actually happened?
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: Application Logs
      description: Please provide relevant logs from the application. You can usually find these in the logs directory or container logs
      placeholder: Paste your logs here
      render: shell
    validations:
      required: true

  - type: textarea
    id: screenshots
    attributes:
      label: Screenshots
      description: If applicable, add screenshots to help explain your problem
      placeholder: Drag and drop images here or paste them
    validations:
      required: false

  - type: textarea
    id: additional-context
    attributes:
      label: Additional Context
      description: Add any other context about the problem here
      placeholder: Any additional information that might be helpful
    validations:
      required: false
