Modifications made to manifest.json to make web store accept it
===============================================================
changed version to 2 (3 is required for service workers, so wonder how that will work)
changed the content_security_policy to a simple string, again, wonder how that will work

Install in Web Store
====================
1. modify manifest.json (outside src folder), and bump version
2. run build.sh
3. https://partner.microsoft.com/en-us/dashboard/microsoftedge/
4. upload app.zip as new version
