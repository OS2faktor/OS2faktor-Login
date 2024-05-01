To run tests, the following must be in place
============================================

* database schemas that must be created (empty is fine)
** os2faktor_nsis
** os2faktor

* /etc/host changes required
** os2faktor-idp should map to 127.0.0.1

* config changes
** running the prepare.sh script should fix this, just don't commit the changes
** make sure os2faktor-nsis-idp is configured with the following (default it runs against production)

os2faktor.common.mfa.baseUrl=http://localhost:9088
os2faktor.common.mfa.apiKey=e0418045-852f-4eb0-84ad-6bd03d95599b

** make sure os2faktor-nsis-ui is configured with the extra bootstrap endpoint that bypasses SAML

saml.nonsecured.pages=/,/privatlivspolitik,/vilkaar,/manage/**,/error,/webjars/**,/css/**,/js/**,/img/**,/favicon.ico,/api/**,/bootstrap/**

* applications that must be running
** os2faktor-nsis-selvbetjening   (mvn spring-boot:run -P test-profile)
** os2faktor-nsis-idp             (mvn spring-boot:run -P test-profile)
** os2faktor MFA backend          (mvn spring-boot:run)


Notes
=====
* When the os2faktor (mfa backend module) starts, it will create all the needed tables for the MFA
* When the testdriver runs, it automatically created users, mfa devices, etc needed for the tests

BUGS
====
Running all tests from the command line will most likely cause some of the to fail. Running each test class
from within Eclipse works though... probably a timing/data error
