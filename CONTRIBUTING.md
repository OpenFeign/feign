# Contributing to Feign
Please read [HACKING](./HACKING.md] prior to raising change.

If you would like to contribute code you can do so through GitHub by forking the repository and sending a pull request (on a branch other than `master` or `gh-pages`).

## Pull Requests
Pull requests eventually need to resolve to a single commit. The commit log should be easy to read as a change log. We use the following form to accomplish that.
* First line is a <=72 character description in present tense, explaining what this does.
  * Ex. "Fixes regression on encoding vnd headers" > "Fixed encoding bug", which forces the reader to look at code to understand impact.
* Do not include issue links in the first line as that makes pull requests look weird.
  * Ex. "Addresses #345" becomes a pull request title: "Addresses #345 #346"
* After the first line, use markdown to concisely summarize the implementation.
  * This isn't in leiu of comments, and it assumes the reader isn't intimately familar with code structure.
* If the change closes an issue, note that at the end of the commit description ex. "Fixes #345"
  * GitHub will automatically close change with this syntax.
* If the change is notable, also update the [change log](./CHANGELOG.md) with your summary description.
  * The unreleased minor version is often a good default.

## Code Style
When submitting code, please ensure you follow the [Google Style Guide](http://google-styleguide.googlecode.com/svn/trunk/javaguide.html). For example, you can format code with IntelliJ 13 using [this file](https://google.github.io/styleguide/intellij-java-google-style.xml) and with IntelliJ 15 using [this file](https://raw.githubusercontent.com/garukun/styleguide/add-intellij-15-java/intellij-15-java-google-style.xml).

## License

By contributing your code, you agree to license your contribution under the terms of the [APLv2](./LICENSE]

All files are released with the Apache 2.0 license.

If you are adding a new file it should have a header like this:

```
/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 ```
