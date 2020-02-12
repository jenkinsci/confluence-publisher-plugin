Changelog
===

### Newer versions

See [GitHub releases](https://github.com/jenkinsci/confluence-publisher-plugin/releases)

### 2.0.5
_Feb 12, 2020_

-   [JENKINS-47309](https://issues.jenkins-ci.org/browse/JENKINS-47309)
    Switch to the Confluence REST API, as the SOAP API is now
    [deprecated](https://developer.atlassian.com/server/confluence/confluence-xml-rpc-and-soap-apis/).
    ([#15](https://github.com/jenkinsci/confluence-publisher-plugin/pull/15)
    [@mslipets](https://github.com/mslipets))
    
    **NOTE**: The REST API is supported as of Confluence 5.5. If you
    use an older version of Confluence, you should continue using a
    previous version of this plugin.

### 2.0.2
_July 30, 2018_

-   [Fix security
    issue](https://jenkins.io/security/advisory/2018-07-30/#SECURITY-982)

### 1.8
_Jan 14, 2013_

-   [JENKINS-15472](https://issues.jenkins-ci.org/browse/JENKINS-15472)
    Create the page in Confluence at build-time if the page didn't
    already exist.

### 1.7.1
_Jun 25, 2012_

-   [JENKINS-14205](https://issues.jenkins-ci.org/browse/JENKINS-14205)
    Fix logic error checking for "start" token for the "Between Markers"
    editor.
-   *Note*: The 1.7 tag failed to release properly from the Maven
    Release plugin, which is why this version is actually released as
    "1.7.1".

### 1.6
_May 29, 2012_

-   [JENKINS-13896](https://issues.jenkins-ci.org/browse/JENKINS-13896)
    Fix StringIndexOutOfBoundsException when the "between" ending marker
    exists in multiple locations.

### 1.5
_Apr 27, 2012_

-   [JENKINS-13569](https://issues.jenkins-ci.org/browse/JENKINS-13569)
    Fix NPE when "Attach archived artifacts" is enabled, but the job's
    "Archive the artifacts" option is **not** enabled.

### 1.4
_Jan 11, 2012_

-   [JENKINS-12253](https://issues.jenkins-ci.org/browse/JENKINS-12253)
    Introduces "even if unstable" option in the job config.
-   [JENKINS-12253](https://issues.jenkins-ci.org/browse/JENKINS-12253)
    Also introduces a `${BUILD_RESULT`} build-time environment variable.
-   [JENKINS-12254](https://issues.jenkins-ci.org/browse/JENKINS-12254)
    Allow Space and Page names to be specified by build-time environment
    variables (e.g., dynamic from a parameterized build).

### 1.3
_Oct 17, 2011_

-   [JENKINS-11276](https://issues.jenkins-ci.org/browse/JENKINS-11276)
    Fixes content editing in Confluence 4.0.
-   Adds a new "Replace entire page" editor, which was previously only
    possible with the "Replace between tokens" editor, and placing the
    start/end tokens at the top/bottom of the page content.

### 1.2
_Oct 16, 2011_

-   [JENKINS-11276](https://issues.jenkins-ci.org/browse/JENKINS-11276)
    Fixes file attachments when working against a Confluence 4.0 server.
    Also fails gracefully if content editors are configured for a 4.0
    server. Both features still work properly on Confluence 3.x, but
    content editing is disabled for version 4.0+.

### 1.1.1
_Sep 22, 2011_

-   Fix a Content-Type issue that prevented attachments to Confluence,
    by defaulting to application/octet-stream if unable to figure out
    the content type automatically.

### 1.1
_Jul 2, 2011_

-   Implements Confluence page [wiki markup
    editing](https://wiki.jenkins-ci.org/display/JENKINS/Confluence+Publisher+Plugin#ConfluencePublisherPlugin-Editingpagemarkup).

### 1.0.3
_Jun 19, 2011_

-   Fix bug introduced in 1.0.2 for Confluence installations hosted at
    the root (e.g., <http://confluence.example.com/>). 1.0.2 works for
    non-root installations (e.g., <http://www.example.com/confluence/>).
    This release should fix both cases.

### 1.0.2
_Jun 18, 2011_

-   **Bad Release!** See version 1.0.3
-   [GH-1](https://github.com/jenkinsci/confluence-publisher-plugin/pull/1) -
    Fixing incorrect confluence RPC URL creation in Utils.

### 1.0.1
_Mar 24, 2011_

-   Rerelease 1.0.0 to properly set required Jenkins version

### 1.0.0
_Feb 28, 2011_

-   Initial release

