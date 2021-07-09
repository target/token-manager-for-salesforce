# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [1.0.1] - 2021-01-21

### Fixed

- Passwords sent to the auth endpoint were not URL encoded which caused requests to fail if the password contained special characters. This version adds URL encoding for passwords.
- Gradle `check` task now depends on `testintegration` task
- Add additional debug logging info to README for RestTemplate requests

## [1.0.0] - 2021-01-13

Initial release
