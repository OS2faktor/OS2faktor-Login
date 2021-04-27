#!/bin/bash

echo "os2faktor.common.mfa.baseUrl=http://localhost:9088" >> ../idp/config/application.properties
echo "os2faktor.common.mfa.apiKey=e0418045-852f-4eb0-84ad-6bd03d95599b" >> ../idp/config/application.properties

echo "saml.nonsecured.pages=/,/privatlivspolitik,/vilkaar,/manage/**,/error,/webjars/**,/css/**,/js/**,/img/**,/favicon.ico,/api/**,/bootstrap/**" >> ../ui/config/application.properties


