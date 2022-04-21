# How to setup a development environment for OS2faktor

## pre-requisites
A running instance of MySQL or MariaDB is needed, and two pre-created database schemas with the names

* os2faktor
* os2faktor_nsis

The schema names are not fixed, but the existing code is preconfigured to run against these, so the configuration
needs to be modified if the schema name are different. Also the username/password for the SQL server is pre-configured
to be

root / Test1234

Running MariaDB in a docker instance is an easy way to do this, otherwise just change the configuration for each application.

## Stuff that is missing from the codebase
Due to size limitations on Github, the Webview binary distribution could not be uploaded to the repository. You'll either need to grab
a copy of microsofts website and put into the folder, or grab the binary installer for the WCP on the www.os2faktor.dk website,
and just expand and copy the folder from there.

Also, to integrate with NemID a certificate is needed - none is supplied with the code, so you'll need a service agreement with
Nets to have a running integration. As the code is moving towards NemLog-in instead of direct NemID integration, I suggest just
using the NemLog-in integration instead, and ignore the direct NemID integration.

## server configuration
It is expected that a Linux machine is used for development. It might be possible to run it on other operating systems, but this
has not been tried, so this guide assumes Linux as the development machine operating system.

It is recommended to add the following entries into /etc/hosts on the development machine

127.0.0.1 frontend-dev.os2faktor.dk
127.0.0.1 admin-dev.os2faktor.dk
127.0.0.1 os2faktor-idp
127.0.0.1 os2faktor-sp

These are used in the various projects

## setup OS2faktor MFA
The codebase for MFA requires Java 17 (it has recently been upgraded from Java 8, and the Java 17 release is expected on the 22nd
of april, so worst case the current codebase is still running Java 8, which may cause various issues).

Note that the codebase for OS2faktor Login runs Java 11, so co-existance of the two Java versions is needed during development.
This is pretty easy on Linux, as you can setup a Java VM path for each terminal window.

### run backend module
This module runs the API's that both clients and connectors interact with. To compile, go into the "backend" folder and compile the code with

$ mvn clean install

Then go into the "backend" folder (inside the first "backend" folder ;)), and modify the development configuration founder under

config/application.properties

* if needed, update the database url, username and password
* the various AWS settings can probably be left empty, but push notifications will not work without them
* the vapid keys must be generated and filled out, otherwise the application will not start (https://vapidkeys.com/ is probably fine for development). Note that the code probably has some random values already, but feel free to change them

now run the software using

$ mvn spring-boot:run

During startup the application will populate the database (os2faktor schema). We will need to create a bit more data, so connect to
the database, and insert the following records

insert into municipalities (cvr, api_key, name) values ('12345678', UUID(), 'Test');
insert into servers (name, api_key, municipality_id) select 'Test', 'Test1234', id from municipalities;
insert into login_service_provider (name, cvr, api_key) values ('Test', '12345678', 'Test1234');

This will create a test-server that allows connections using the secret key "Test1234"

### run client-frontend module
This module runs the HTML pages that clients use during registration, as well as the online self-service website. To compile, go into the "client-frontend"
folder, and update the configuration in config/application.properties

* if needed, update the database url, username and password
* the various AWS settings can probably be left empty, but push notifications will not work without them
* the backend apiKey and URL should match the running backend setup in the previous setp
* the IdP used to "simulate" the NemLog-in infrastructure... an actual connection to NemLog-in can be used, though it is usually easier to run a local IdP, with more control over the users and credentials

Then run the code with

$ mvn spring-boot:run

This should start the frontend on port 9121, and it can be tested by accessing

https://frontend-dev.os2faktor.dk:9121

Note that accessing it might be easier once the OS2faktor Login has been setup - then change the SAML settings to point to that to handle login.

## setup OS2faktor Login
The Login codebase requires Java 11, and consists of two mandatory components and some extra components that are not covered here. The mandatory
components are the selfservice (ui) module and the actual identity provider (idp) module.

### compile and run the IdP module
Start by compiling everything from the root of the source folder

$ mvn clean install

then enter the idp folder and modify the configuration under config/application.properties

* point the NemID keystore to some file that exists (if you don't need NemID, just point it to some random keystore, otherwise you need to point to an actual keystore that works with NemID)
* update the database connection info if needed
* update the MFA baseurl and apiKey to point to the locally running backend (the one setup in the previous chapter, with the "Test1234" apiKey)
* set the CVR setting to 12345678 (to match the municipality created in the OS2faktor MFA system)

finally run with

$ mvn spring-boot:run

this will generate the database schema for os2faktor_nsis, and the IdP will now be running at

https://os2faktor-idp:8809/

### compile and run the selfservice module
This is located in the ui folder, so go there and modify the config/application.properties file

* update the database connection info if needed
* update the MFA baseurl and apiKey to point to the locally running backend (the one setup in the previous chapter, with the "Test1234" apiKey)
* set the CVR setting to 12345678 (to match the municipality created in the OS2faktor MFA system)

and then run with

$ mvn spring-boot:run

to run the frontend. It is now running at

https://os2faktor-sp:8808/

It should work with NemLog-in out of the box - and using "Test Login" under NemLog-in works with the user

Ellie999 / Test1234

After login, bootstrap the first MFA client by using the build-in "Tilknyt 2-faktor enhed" button on "Min identitet". Note that this works
best with the hardware key clients, as you'll otherwise need to build a custom MFA client to interact with the application running on your
machine.

# Other components
The above guide only covers the core components. There are codebases for all the various connectors, clients and integrations, which are also
available in the repository. These are not covered in this mini-guide.
