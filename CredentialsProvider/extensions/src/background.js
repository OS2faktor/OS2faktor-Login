chrome.webRequest.onBeforeRequest.addListener(
    function(details) {
        if (details.url.indexOf("os2faktorAutoClose=true") > -1) {
            var sessionUrl = details.url.substring(0, details.url.indexOf("os2faktorAutoClose=true") -1);

            fetch(sessionUrl, {
                method: 'GET',
                credentials: 'include',
                mode: 'no-cors'
            })
            .then(
                function(response) {
                    if (response.status != 200 && response.status != 0) {
                        console.log('Error response. Status Code: ' + response.status);
                        return;
                    }
                }
            )
            .catch(function(err) {
                console.log('Fetch Error', err);
            });

            return { cancel: true };
        }

        return;
    },
    {
        urls: [
            "https://*/sso/saml/client/login"
        ]
    },
        ["blocking"]
);
