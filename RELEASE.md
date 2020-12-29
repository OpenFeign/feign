# Feign Release Process

This repo uses [semantic versions](http://semver.org/). Please keep this in mind when choosing version numbers.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/OpenFeign/feign) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   Prepare the next release by running the [release script](scripts/release.sh) from a clean checkout of the master branch.
   This script will:
   * Update all versions to the next release.
   * Tag the release.
   * Update all versions to the next development version.

1. **Wait for CI**

   This part is controlled by the [CircleCI configuration](.circleci/config.yml), specifically the `deploy` job.  Which
   creates the release artifacts and deploys them to maven central.

## Credentials

Credentials of various kind are needed for the release process to work. If you notice something
failing due to unauthorized, you will need to modify the stored values in `Sonatype` [CircleCI Context](https://circleci.com/docs/2.0/contexts/)
for the OpenFeign organization.

`SONATYPE_USER` - the username of the Sonatype account used to upload artifacts.
`SONATYPE_PASSWORD` - password for the Sonatype account.
`GPG_KEY` - the gpg key used to sign the artifacts.
`GPG_PASSPHRASE` - the passphrase for the gpg key

### Troubleshooting invalid credentials

If the `deploy` job fails due to invalid credentials, double check the `SONATYPE_USER` and `SONATYPE_PASSWORD`
variables first and correct them.

### Troubleshooting GPG issues

If the `deploy` job fails when signing artifacts, the GPG key may have expired or is incorrect.  To update the
`GPG_KEY`, you must export a valid GPG key to ascii and replace all newline characters with `\n`.  This will
allow CircleCi to inject the key into the environment in a way where it can be imported again.  Use the following command
to generate the key file.

```shell
gpg -a --export-secret-keys | cat -e | sed  | sed 's/\$/\\n/g' > gpg_key.asc
```

Paste the contents of this file into the `GPG_KEY` variable in the context and try the job again.

## First release of the year

The license plugin verifies license headers of files include a copyright notice indicating the years a file was affected.
This information is taken from git history. There's a once-a-year problem with files that include version numbers (pom.xml).
When a release tag is made, it increments version numbers, then commits them to git. On the first release of the year,
further commands will fail due to the version increments invalidating the copyright statement. The way to sort this out is
the following:

Before you do the first release of the year, move the SNAPSHOT version back and forth from whatever the current is.
In-between, re-apply the licenses.
```bash
$ ./mvnw versions:set -DnewVersion=1.3.3-SNAPSHOT -DgenerateBackupPoms=false
$ ./mvnw com.mycila:license-maven-plugin:format
$ ./mvnw versions:set -DnewVersion=1.3.2-SNAPSHOT -DgenerateBackupPoms=false
$ git commit -am"Adjusts copyright headers for this year"
```
