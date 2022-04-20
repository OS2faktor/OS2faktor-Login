function obtainSession(sessionUrl) {
    chrome.windows.create({
            url: sessionUrl,
            type: "popup",
            state: chrome.windows.WindowState.MINIMIZED
    }, function(win) {
        setTimeout( () => { chrome.windows.remove(win.id); }, 1000);
    });
}

chrome.storage.managed.onChanged.addListener((changes, areaName) => {
    if (changes == null || changes.timestamp.newValue == null || changes.token.newValue == null) {
        return;
    }

    //Check that timestamp matches
    var oldestAllowedDate = new Date();
    var oldestAllowedMessageInMinutes = 5;
    oldestAllowedDate.setMinutes(oldestAllowedDate.getMinutes() - oldestAllowedMessageInMinutes);

    var timestamp = new Date(changes.timestamp.newValue);
    if (oldestAllowedDate > timestamp) {
        return;
    }

    //Obtain a session to SSO login
    obtainSession(changes.token.newValue);
});
