# Fine-Grained Traceability Code

All the code here is configured to use Gradle. Importing the [top-level project](./build.gradle)
into your favorite IDE will allow for easy building and running of all experiments. This is
well-supported on [IntelliJ IDEA](https://www.jetbrains.com/idea/).

## Instructions

1. Initialize the submodules with the script [update-all](./update-all).

    - NOTE: If you haven't cloned the repository, you can use `git clone --recurse-submodules` to
      clone and initialize submodules at the same time.

2. Import the top-level Gradle project [build.gradle](./build.gradle) into your IDE of choice.

3. Use the script [update-all](./update-all) for pulling or after switching branches to
   automatically set up submodules and avoid errors.

## Troubleshooting

Assuming the top-level Gradle project was imported into your IDE correctly, most build issues will
be caused by the submodules being out of sync. These should be fixed by running the `update all`
script (or equivalent git commands in the command line).

## Suggested configuration

- Set
  up [version control on IntelliJ](https://www.jetbrains.com/help/idea/set-up-a-git-repository.html#put-existing-project-under-Git)
  to manage Git through a GUI and minimize errors. The IDE also handles pulling and updating
  submodules, so it is not necessary to use the update script. Make sure to add the whole repository
  as well as each submodule as a Git root.

- The submodules are configured to use SSH. You should
  [set up an SSH key with GitHub](https://docs.github.com/en/free-pro-team@latest/github/authenticating-to-github/connecting-to-github-with-ssh)
  to be able to pull/push without getting a password prompt (IntelliJ can remember your SSH
  passcode).

- Configure the SSH [agent](https://superuser.com/a/1114257) to remember the SSH passkey so that it
  only needs to be entered once per session (simply add `AddKeysToAgent yes` to `~/.ssh/config`)

## Contents

### Projects

1. *[Lasso](./Lasso)*: Implements the static-analysis-based detectors for constraint implementation
   patterns.
2. *[Lasso/sample](./Lasso/sample)*: A sample project used to test the detectors. The code here is
   used in test suites in the Lasso project, and is kept as a subproject to simplify editing and to
   let the IDE check that the code is valid.
4. *[seers-base](./seers-base)*: Base library containing common functionality for SEERS projects.
5. *[Lasso/semantic-vectors](./Lasso/semantic-vectors)*: Container project to enable Lasso to use a
   more recent Lucene version than SemanticVectors (implements the LSI baseline).

### Scripts

1. `update-all`: Initializes/updates submodules and pulls all changes.
2. `evaluate-lasso`: Evaluates constraint tracer using the constraint data in
   [/pattern-coding/traced-constraints.csv](../pattern-coding/traced-constraints.csv)
3. `evaluate-detectors`: Evaluates the flow detectors.
