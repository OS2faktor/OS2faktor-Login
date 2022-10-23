//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
//

#ifndef WIN32_NO_STATUS
#include <ntstatus.h>
#define WIN32_NO_STATUS
#endif
#include <unknwn.h>
#include "Credential.h"
#include "guid.h"
#include "loguru.hpp"
#include <windows.h>
#include <strsafe.h>
#include <new>
#include <string>
#include <direct.h>
#include <userenv.h>
#include <stdlib.h>
#include <Lmwksta.h>
#include <StrSafe.h>
#include <Security.h>
#include <lmerr.h>
#include <locale>
#include <codecvt>
using namespace std;



// A credential only references one user (or NULL) 
// The other important class CredentialProvider will create a credential when we know which user (or NULL) that we are dealing with
Credential::Credential() :
	_cRef(1),
	_pCredProvCredentialEvents(nullptr),
	_pszUserSid(nullptr),
	_pszQualifiedUserName(nullptr),
	_fIsLocalUser(false),
	_fChecked(false),
	_fShowControls(false),
	_dwComboIndex(0),
	_IsLoggingEnabled(false),
	_IsUPNCacheEnabled(false),
	_hKey(nullptr),
	_IsAnonUser(false),
	_IsStatusPasswordMustChange(false),
	_IsPasswordChangeBeforeLoginSuccess(false)
{
	DllAddRef();

	ZeroMemory(_rgCredProvFieldDescriptors, sizeof(_rgCredProvFieldDescriptors));
	ZeroMemory(_rgFieldStatePairs, sizeof(_rgFieldStatePairs));
	ZeroMemory(_rgFieldStrings, sizeof(_rgFieldStrings));

	// When initializing the credential we need to set up logging and fetch other configurations for starting processes and so on.
	// Here we open a HKEY (Access to windows registry) and if successfull we setup logging
	try
	{
		HKEY hKey;
		wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin";
		LSTATUS lResult = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
		if (lResult == ERROR_SUCCESS)
		{
			_hKey = hKey;

			// This will actually only log IF the CredentialProviderLogPath key is set in the registry, the default provided path is just for safety
			wstring logPath;
			LONG getValueResult = GetStringRegKey(hKey, L"CredentialProviderLogPath", logPath, L"C:/Logs/OS2faktorLogin-CredentialsProvider/CredentialsProvider.txt");
			if (getValueResult == ERROR_SUCCESS)
			{
				// Convert from wstring -> string
				using convert_type = std::codecvt_utf8<wchar_t>;
				std::wstring_convert<convert_type, wchar_t> converter;
				std::string converted_str = converter.to_bytes(logPath);

				// Add logging
				loguru::add_file(converted_str.c_str(), loguru::Append, 1);
				_IsLoggingEnabled = true;
			}

			bool upnEnabled;
			getValueResult = GetBoolRegKey(hKey, L"upnCacheEnabled", upnEnabled, false);
			if (getValueResult == ERROR_SUCCESS)
			{
				_IsUPNCacheEnabled = upnEnabled;
			}
		}
	}
	catch (const std::exception&)
	{
		_IsLoggingEnabled = false;
	}
}

Credential::~Credential()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "~Credential called");
	}

	// Here we securely zero the memory adresses that held the password variable.
	// We dont want another program to be able to read it after we have released the memory
	if (_rgFieldStrings[FI_PASSWORD])
	{
		size_t lenPassword = wcslen(_rgFieldStrings[FI_PASSWORD]);
		SecureZeroMemory(_rgFieldStrings[FI_PASSWORD], lenPassword * sizeof(*_rgFieldStrings[FI_PASSWORD]));

		size_t lenNewPassword = wcslen(_rgFieldStrings[FI_NEW_PASSWORD]);
		SecureZeroMemory(_rgFieldStrings[FI_NEW_PASSWORD], lenNewPassword * sizeof(*_rgFieldStrings[FI_NEW_PASSWORD]));

		size_t lenNewPasswordConfirm = wcslen(_rgFieldStrings[FI_NEW_PASSWORD_CONFIRM]);
		SecureZeroMemory(_rgFieldStrings[FI_NEW_PASSWORD_CONFIRM], lenNewPasswordConfirm * sizeof(*_rgFieldStrings[FI_NEW_PASSWORD_CONFIRM]));
	}

	// Then we release the rest of the memory, this information dosnt have to be securely erased
	for (int i = 0; i < ARRAYSIZE(_rgFieldStrings); i++)
	{
		CoTaskMemFree(_rgFieldStrings[i]);
		CoTaskMemFree(_rgCredProvFieldDescriptors[i].pszLabel);
	}

	// This releases memory for the user SID (A identifier for the user) and the Qualified username 
	CoTaskMemFree(_pszUserSid);
	CoTaskMemFree(_pszQualifiedUserName);

	// This closes the registry key we opened when we created this credential
	RegCloseKey(_hKey);
	DllRelease();

	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "~Credential done");
	}
}


// Initializes one credential with the field information passed in.
// Here we can descide what each field contains depending on the user and usage scenario we get passed in cpus and pcpUser
HRESULT Credential::Initialize(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus,
	_In_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR const* rgcpfd,
	_In_ FIELD_STATE_PAIR const* rgfsp,
	_In_opt_ ICredentialProviderUser* pcpUser)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "Initialize called");
	}
	HRESULT hr = S_OK;
	_cpus = cpus;

	if (pcpUser != nullptr)
	{
		GUID guidProvider;
		pcpUser->GetProviderID(&guidProvider);
		_fIsLocalUser = (guidProvider == Identity_LocalUserProvider);
	}
	else {
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "Creating credential for anonymous login");
		}
		_IsAnonUser = true;
	}

	// Copy the field descriptors for each field. This is useful if you want to vary the field
	// descriptors based on what Usage scenario the credential was created for.
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "Copying the field descriptors for each field");
	}

	for (DWORD i = 0; SUCCEEDED(hr) && i < ARRAYSIZE(_rgCredProvFieldDescriptors); i++)
	{
		_rgFieldStatePairs[i] = rgfsp[i];
		hr = FieldDescriptorCopy(rgcpfd[i], &_rgCredProvFieldDescriptors[i]);
	}



	if (cpus == CPUS_CHANGE_PASSWORD) {
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Showing username and new password fields and hiding reset password link");
		}

		_rgFieldStatePairs[FI_USERNAME].cpfs = CPFS_DISPLAY_IN_BOTH;
		_rgFieldStatePairs[FI_NEW_PASSWORD].cpfs = CPFS_DISPLAY_IN_BOTH;
		_rgFieldStatePairs[FI_NEW_PASSWORD_CONFIRM].cpfs = CPFS_DISPLAY_IN_BOTH;
		_rgFieldStatePairs[FI_RESET_PASSWORD_LINK].cpfs = CPFS_HIDDEN;
	}


	// Initialize the String value of all the fields.
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "Initializing the String value of all the fields");
	}

	if (SUCCEEDED(hr))
	{
		// Hover over text for "more sign-in options" tile
		hr = SHStrDupW(L"Login", &_rgFieldStrings[FI_LABEL]);
	}
	if (SUCCEEDED(hr))
	{
		hr = SHStrDupW(L"OS2faktor Credential Provider", &_rgFieldStrings[FI_LARGE_TEXT]);
	}
	if (SUCCEEDED(hr))
	{
		// Password field
		hr = SHStrDupW(L"", &_rgFieldStrings[FI_PASSWORD]);
	}
	if (SUCCEEDED(hr))
	{
		// New password field
		hr = SHStrDupW(L"", &_rgFieldStrings[FI_NEW_PASSWORD]);
	}
	if (SUCCEEDED(hr))
	{
		// New password confirm field
		hr = SHStrDupW(L"", &_rgFieldStrings[FI_NEW_PASSWORD_CONFIRM]);
	}
	if (SUCCEEDED(hr))
	{
		// Submit button
		hr = SHStrDupW(L"Log ind", &_rgFieldStrings[FI_SUBMIT_BUTTON]);
	}
	if (SUCCEEDED(hr))
	{
		// Link for opening the change password dialog. The text is configurable, if no key "ResetPasswordLinkText" is present a default value will be used
		try
		{
			HKEY hKey;
			wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin";
			LSTATUS lResult = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
			if (lResult == ERROR_SUCCESS)
			{
				wstring resetPasswordLinkText;
				LONG getValueResult = GetStringRegKey(hKey, L"ResetPasswordLinkText", resetPasswordLinkText, L"Jeg har glemt mit kodeord");
				hr = SHStrDupW(resetPasswordLinkText.c_str(), &_rgFieldStrings[FI_RESET_PASSWORD_LINK]);
			}
			RegCloseKey(hKey);
		}
		catch (const std::exception&)
		{
			hr = SHStrDupW(L"Jeg har glemt mit kodeord", &_rgFieldStrings[FI_RESET_PASSWORD_LINK]);
		}
	}
	if (SUCCEEDED(hr) && !_IsAnonUser)
	{
		// This fetches the qualified username of the user (DOMAIN\Username) 
		hr = pcpUser->GetStringValue(PKEY_Identity_QualifiedUserName, &_pszQualifiedUserName);
	}
	if (SUCCEEDED(hr) && !_IsAnonUser)
	{
		// This gets the users SID (Returning a valid SID (or NULL) is required of a V2 CredentialProvider)
		hr = pcpUser->GetSid(&_pszUserSid);
	}
	if (SUCCEEDED(hr))
	{
		// Username field, only shown when we don't know the user or on change password

		if (!(cpus == CPUS_CHANGE_PASSWORD))
		{
			hr = SHLocalStrDupW(L"", &_rgFieldStrings[FI_USERNAME]);
		}
		else
		{
			// In the case of CPUS_CHANGE_PASSWORD we prefill the username field
			hr = SHLocalStrDupW(_pszQualifiedUserName, &_rgFieldStrings[FI_USERNAME]);
		}
	}



	if (_IsLoggingEnabled)
	{
		if (SUCCEEDED(hr))
		{
			LOG_F(INFO, "Field names initialized");
		}
		else {
			LOG_F(ERROR, "Error in field name initialisation");

		}
	}

	return hr;
}

// LogonUI calls this in order to give us a callback in case we need to notify it of anything.
HRESULT Credential::Advise(_In_ ICredentialProviderCredentialEvents* pcpce)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "Advise called");
	}
	if (_pCredProvCredentialEvents != nullptr)
	{
		_pCredProvCredentialEvents->Release();
	}
	return pcpce->QueryInterface(IID_PPV_ARGS(&_pCredProvCredentialEvents));
}

// LogonUI calls this to tell us to release the callback.
HRESULT Credential::UnAdvise()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "UnAdvise called");
	}
	if (_pCredProvCredentialEvents)
	{
		_pCredProvCredentialEvents->Release();
	}
	_pCredProvCredentialEvents = nullptr;
	return S_OK;
}

// LogonUI calls this function when our tile is selected (zoomed)
// If you simply want fields to show/hide based on the selected state,
// there's no need to do anything here - you can set that up in the
// field definitions. But if you want to do something
// more complicated, like change the contents of a field when the tile is
// selected, you would do it here.
HRESULT Credential::SetSelected(_Out_ BOOL* pbAutoLogon)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "SetSelected called");
		LOG_F(INFO, "_IsAnonUser = %s", _IsAnonUser ? "true" : "false");
	}

	if (_IsAnonUser)
	{
		_pCredProvCredentialEvents->BeginFieldUpdates();
		_pCredProvCredentialEvents->SetFieldState(nullptr, FI_USERNAME, CPFS_DISPLAY_IN_BOTH);

		// If the username field has any input, focus the password field. this happens on wrong password/username 
		if (wcslen(_rgFieldStrings[FI_USERNAME]) == 0)
		{
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_USERNAME, CPFIS_FOCUSED);
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_PASSWORD, CPFIS_NONE);
		}
		else {
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_USERNAME, CPFIS_NONE);
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_PASSWORD, CPFIS_FOCUSED);
		}
		
		_pCredProvCredentialEvents->EndFieldUpdates();
	}


	if (_IsPasswordChangeBeforeLoginSuccess)
	{
		*pbAutoLogon = TRUE;
		_IsPasswordChangeBeforeLoginSuccess = false;
	}
	else {
		*pbAutoLogon = FALSE;
	}
	return S_OK;
}

// Similarly to SetSelected, LogonUI calls this when your tile was selected
// and now no longer is. The most common thing to do here (which we do below)
// is to clear out the password field.
HRESULT Credential::SetDeselected()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "SetDeselected called");
		LOG_F(1, "_IsAnonUser: %d", _IsAnonUser);
	}
	HRESULT hr = S_OK;

	if (_IsAnonUser)
	{
		_pCredProvCredentialEvents->BeginFieldUpdates();
		_pCredProvCredentialEvents->SetFieldState(nullptr, FI_USERNAME, CPFS_HIDDEN);
		_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_USERNAME, CPFIS_NONE);
		_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_PASSWORD, CPFIS_FOCUSED);
		_pCredProvCredentialEvents->EndFieldUpdates();
	}

	// Zero the memory holding password and set value to empty string
	if (_rgFieldStrings[FI_PASSWORD])
	{
		size_t lenPassword = wcslen(_rgFieldStrings[FI_PASSWORD]);
		SecureZeroMemory(_rgFieldStrings[FI_PASSWORD], lenPassword * sizeof(*_rgFieldStrings[FI_PASSWORD]));

		CoTaskMemFree(_rgFieldStrings[FI_PASSWORD]);
		hr = SHStrDupW(L"", &_rgFieldStrings[FI_PASSWORD]);

		if (SUCCEEDED(hr) && _pCredProvCredentialEvents)
		{
			_pCredProvCredentialEvents->SetFieldString(this, FI_PASSWORD, _rgFieldStrings[FI_PASSWORD]);
		}
	}

	// Set value of username to empty string
	if (_rgFieldStrings[FI_USERNAME] && _cpus != CPUS_CHANGE_PASSWORD)
	{
		ZeroMemory(_rgFieldStrings[FI_USERNAME], sizeof(_rgFieldStrings[FI_USERNAME]));

		CoTaskMemFree(_rgFieldStrings[FI_USERNAME]);
		hr = SHStrDupW(L"", &_rgFieldStrings[FI_USERNAME]);

		if (SUCCEEDED(hr) && _pCredProvCredentialEvents)
		{
			_pCredProvCredentialEvents->SetFieldString(this, FI_USERNAME, _rgFieldStrings[FI_USERNAME]);
		}
	}

	return hr;
}

// Get info for a particular field of a tile. Called by logonUI to get information
// to display the tile.
HRESULT Credential::GetFieldState(DWORD dwFieldID,
	_Out_ CREDENTIAL_PROVIDER_FIELD_STATE* pcpfs,
	_Out_ CREDENTIAL_PROVIDER_FIELD_INTERACTIVE_STATE* pcpfis)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetFieldState called");
	}
	HRESULT hr;

	// Make sure winlogon is not calling an index that we do not have, 
	// and then return the Field state and Field Interactive state if we have the index
	if ((dwFieldID < ARRAYSIZE(_rgFieldStatePairs)))
	{
		*pcpfs = _rgFieldStatePairs[dwFieldID].cpfs;
		*pcpfis = _rgFieldStatePairs[dwFieldID].cpfis;
		hr = S_OK;
	}
	else
	{
		hr = E_INVALIDARG;
	}
	return hr;
}

// Sets ppwsz to the string value of the field at the index dwFieldID
HRESULT Credential::GetStringValue(DWORD dwFieldID, _Outptr_result_nullonfailure_ PWSTR* ppwsz)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetStringValue called");
	}
	HRESULT hr;
	*ppwsz = nullptr;

	// Check to make sure dwFieldID is a legitimate index
	if (dwFieldID < ARRAYSIZE(_rgCredProvFieldDescriptors))
	{
		// Make a copy of the string and return that. The caller
		// is responsible for freeing it.
		hr = SHStrDupW(_rgFieldStrings[dwFieldID], ppwsz);
	}
	else
	{
		hr = E_INVALIDARG;
	}

	return hr;
}

// Get the image to show in the user tile
HRESULT Credential::GetBitmapValue(DWORD dwFieldID, _Outptr_result_nullonfailure_ HBITMAP* phbmp)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetBitmapValue called");
	}
	HRESULT hr;
	*phbmp = nullptr;

	// Validate that windows is asking for the bitmap value of our tile image and not some other index
	if ((FI_TILEIMAGE == dwFieldID))
	{
		HBITMAP hbmp = LoadBitmap(HINST_THISDLL, MAKEINTRESOURCE(IDB_BITMAP1)); // IDB_BITMAP1 is what links our logo.bmp resource to the code
		if (hbmp != nullptr)
		{
			hr = S_OK;
			*phbmp = hbmp;
		}
		else
		{
			hr = HRESULT_FROM_WIN32(GetLastError());
		}
	}
	else
	{
		hr = E_INVALIDARG;
	}

	return hr;
}

// Sets pdwAdjacentTo to the index of the field the submit button should be
// adjacent to. We recommend that the submit button is placed next to the last
// field which the user is required to enter information in. Optional fields
// should be below the submit button.
HRESULT Credential::GetSubmitButtonValue(DWORD dwFieldID, _Out_ DWORD* pdwAdjacentTo)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetSubmitButtonValue called");
	}
	HRESULT hr;

	if (FI_SUBMIT_BUTTON == dwFieldID)
	{
		// pdwAdjacentTo is a pointer to the fieldID you want the submit button to
		// appear next to.

		if (_cpus == CPUS_CHANGE_PASSWORD) {
			*pdwAdjacentTo = FI_NEW_PASSWORD_CONFIRM;
		}
		else {
			*pdwAdjacentTo = FI_PASSWORD;
		}
		hr = S_OK;
	}
	else
	{
		hr = E_INVALIDARG;
	}
	return hr;
}

// Sets the value of a field which can accept a string as a value.
// This is called on each keystroke when a user types into an edit field
HRESULT Credential::SetStringValue(DWORD dwFieldID, _In_ PCWSTR pwz)
{
	HRESULT hr;

	// Validate parameters. Check that windows is not calling out of bounds and that the type of the field is either an edit text (username field) or a password text (password field)
	if (dwFieldID < ARRAYSIZE(_rgCredProvFieldDescriptors) &&
		(CPFT_EDIT_TEXT == _rgCredProvFieldDescriptors[dwFieldID].cpft ||
			CPFT_PASSWORD_TEXT == _rgCredProvFieldDescriptors[dwFieldID].cpft))
	{
		PWSTR* ppwszStored = &_rgFieldStrings[dwFieldID];
		CoTaskMemFree(*ppwszStored);
		hr = SHStrDupW(pwz, ppwszStored);
	}
	else
	{
		hr = E_INVALIDARG;
	}

	return hr;
}

// Returns whether a checkbox is checked or not as well as its label.
// We have no checkboxes so we just return an error to windows if it calls this
HRESULT Credential::GetCheckboxValue(DWORD dwFieldID, _Out_ BOOL* pbChecked, _Outptr_result_nullonfailure_ PWSTR* ppwszLabel)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(ERROR, "GetCheckboxValue called");
	}
	HRESULT hr = E_INVALIDARG;
	*ppwszLabel = nullptr;
	return hr;
}

// Sets whether the specified checkbox is checked or not.
// We have no checkboxes so we just return an error to windows if it calls this
HRESULT Credential::SetCheckboxValue(DWORD dwFieldID, BOOL bChecked)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(ERROR, "SetCheckboxValue called");
	}
	HRESULT hr = E_INVALIDARG;
	return hr;
}

// Returns the number of items to be included in the combobox (pcItems), as well as the
// currently selected item (pdwSelectedItem).
// We have no ComboBoxes so we just return an error to windows if it calls this
HRESULT Credential::GetComboBoxValueCount(DWORD dwFieldID, _Out_ DWORD* pcItems, _Deref_out_range_(< , *pcItems) _Out_ DWORD* pdwSelectedItem)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(ERROR, "GetComboBoxValueCount called");
	}
	HRESULT hr = E_INVALIDARG;
	*pcItems = 0;
	*pdwSelectedItem = 0;
	return hr;
}

// Called iteratively to fill the combobox with the string (ppwszItem) at index dwItem.
// We have no ComboBoxes so we just return an error to windows if it calls this
HRESULT Credential::GetComboBoxValueAt(DWORD dwFieldID, DWORD dwItem, _Outptr_result_nullonfailure_ PWSTR* ppwszItem)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(ERROR, "GetComboBoxValueAt called");
	}
	HRESULT hr = E_INVALIDARG;
	*ppwszItem = nullptr;
	return hr;
}

// Called when the user changes the selected item in the combobox.
// We have no ComboBoxes so we just return an error to windows if it calls this
HRESULT Credential::SetComboBoxSelectedValue(DWORD dwFieldID, DWORD dwSelectedItem)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(ERROR, "SetComboBoxSelectedValue called");
	}
	HRESULT hr = E_INVALIDARG;
	return hr;
}

// Called when the user clicks a command link.
// We have one command link in our implementation, it is used for opening the reset password dialog
HRESULT Credential::CommandLinkClicked(DWORD dwFieldID)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "CommandLinkClicked called");
	}
	HRESULT hr = S_OK;

	// Validate parameter. Check that the called inxed is of type command link
	if (dwFieldID < ARRAYSIZE(_rgCredProvFieldDescriptors) &&
		(CPFT_COMMAND_LINK == _rgCredProvFieldDescriptors[dwFieldID].cpft))
	{
		HWND hwndOwner = nullptr;

		// This switch goes through all the different command links we have, 
		// if we need to add more in the future they should also be added as a case in this switch with the logic that should happen once they are clicked inside
		switch (dwFieldID)
		{
		case FI_RESET_PASSWORD_LINK:
			if (_pCredProvCredentialEvents)
			{
				_pCredProvCredentialEvents->OnCreatingWindow(&hwndOwner);
			}

			//Reset password dialog should be opened
			_StartResetPasswordProcess();

			break;
		default:
			hr = E_INVALIDARG;
		}
	}
	else
	{
		hr = E_INVALIDARG;
	}

	return hr;
}

// Collect the username and password into a serialized credential for the correct usage scenario.
// LogonUI then passes these credentials back to the system to log on.
// This is an important method that is called when the user submits their username/password for login. Here we serialize the information and passes it to windows for verification,
// but we also make our own call to the OS2Faktor backend for verifying the credentials for Single SignOn
HRESULT Credential::GetSerialization(_Out_ CREDENTIAL_PROVIDER_GET_SERIALIZATION_RESPONSE* pcpgsr,
	_Out_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION* pcpcs,
	_Outptr_result_maybenull_ PWSTR* ppwszOptionalStatusText,
	_Out_ CREDENTIAL_PROVIDER_STATUS_ICON* pcpsiOptionalStatusIcon)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "GetSerialization called");
	}

	// Initialize values 
	HRESULT hr = E_UNEXPECTED;
	*pcpgsr = CPGSR_NO_CREDENTIAL_NOT_FINISHED;
	*ppwszOptionalStatusText = nullptr;
	*pcpsiOptionalStatusIcon = CPSI_NONE;
	ZeroMemory(pcpcs, sizeof(*pcpcs));


	// Depending on the usage scenario we now need to do serilize the object differently
	if (_cpus == CPUS_CHANGE_PASSWORD || _IsStatusPasswordMustChange)
	{
		// In the case of change password scenario 
		// OR if we have been told that the user needs to change password prior to log in, 
		// we will use a specific object for serilization
		// In case of change password we use KERB_CHANGEPASSWORD_REQUEST

		// Make sure the passwords are not 0 length
		if (wcslen(_rgFieldStrings[FI_NEW_PASSWORD]) > 0 && wcslen(_rgFieldStrings[FI_NEW_PASSWORD_CONFIRM]) > 0)
		{
			// Validate that "new password" and "new password confirm" matches content.
			if (wcscmp(_rgFieldStrings[FI_NEW_PASSWORD], _rgFieldStrings[FI_NEW_PASSWORD_CONFIRM]) == 0)
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(1, "GetSerialization - Change password: New passwords were non zero and matching");
				}

				// Get Domain and username
				PWSTR pszDomain{};
				PWSTR pszUsername{};

				if (_IsAnonUser)
				{
					// For Anonymous users we need to fetch domain the machine we are running on is joined to.
					PWKSTA_INFO_100 info;
					hr = NetWkstaGetInfo(NULL, 100, (LPBYTE*)&info);
					if (SUCCEEDED(hr))
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(1, "GetSerialization: NetWkstaGetInfo fetched");
						}

						hr = SHStrDupW(info->wki100_langroup, &pszDomain);

						if (SUCCEEDED(hr))
						{
							if (_IsLoggingEnabled)
							{
								LOG_F(1, "GetSerialization: Domain copied to variable");
							}
							hr = SHStrDupW(_rgFieldStrings[FI_USERNAME], &pszUsername);
						}
					}
					CoTaskMemFree(info);
				}
				else {
					hr = SplitDomainAndUsername(_pszQualifiedUserName, &pszDomain, &pszUsername);
				}

				if (SUCCEEDED(hr))
				{
					if (_IsLoggingEnabled)
					{
						LOG_F(1, "GetSerialization - Change password: SplitDomainAndUsername success");
					}

					// Copy old password
					PWSTR pwzProtectedOldPassword;
					hr = ProtectIfNecessaryAndCopyPassword(_rgFieldStrings[FI_PASSWORD], _cpus, &pwzProtectedOldPassword);

					if (SUCCEEDED(hr))
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(1, "GetSerialization - Change password: ProtectIfNecessaryAndCopyPassword success (old/current password)");
						}

						// Copy new password
						PWSTR pwzProtectedNewPassword;
						hr = ProtectIfNecessaryAndCopyPassword(_rgFieldStrings[FI_NEW_PASSWORD], _cpus, &pwzProtectedNewPassword);

						if (SUCCEEDED(hr))
						{
							if (_IsLoggingEnabled)
							{
								LOG_F(1, "GetSerialization - Change password: ProtectIfNecessaryAndCopyPassword success (new password)");
							}

							// Create the KERB_CHANGEPASSWORD_REQUEST object and initialize it with the fetched values
							KERB_CHANGEPASSWORD_REQUEST kcpr;
							hr = KerbChangePasswordRequestInit(pszDomain, pszUsername, pwzProtectedOldPassword, pwzProtectedNewPassword, &kcpr);
							if (SUCCEEDED(hr))
							{
								if (_IsLoggingEnabled)
								{
									LOG_F(1, "GetSerialization - Change password: KerbChangePasswordRequestInit success");
								}

								// Pack it for returning serialized data to windows
								hr = KerbChangePasswordPack(kcpr, &pcpcs->rgbSerialization, &pcpcs->cbSerialization);
								if (SUCCEEDED(hr))
								{
									if (_IsLoggingEnabled)
									{
										LOG_F(1, "GetSerialization - Change password: KerbChangePasswordPack success");
									}

									ULONG ulAuthPackage;
									hr = RetrieveNegotiateAuthPackage(&ulAuthPackage);
									if (SUCCEEDED(hr))
									{
										if (_IsLoggingEnabled)
										{
											LOG_F(1, "GetSerialization - Change password: RetrieveNegotiateAuthPackage success");
										}

										pcpcs->ulAuthenticationPackage = ulAuthPackage;
										pcpcs->clsidCredentialProvider = CLSID_CSample;

										// We cant call c# from here like in the login case,
										// since we first need to know if the change password was successfull before we change it on os2faktor login too.
										// This can be achieved from report result since it gets both the status code and still knows the usage scenario and password

										// Serilization finished
										*pcpgsr = CPGSR_RETURN_CREDENTIAL_FINISHED;
									}
								}

							}
							CoTaskMemFree(pwzProtectedNewPassword);
						}
						CoTaskMemFree(pwzProtectedOldPassword);
					}
					CoTaskMemFree(pszDomain);
					CoTaskMemFree(pszUsername);
				}
			}
			else {
				// "new password" and "new password confirm" does not match.
				*pcpgsr = CPGSR_NO_CREDENTIAL_NOT_FINISHED; // This means we are not returning any serialized data and we are not finished implying that the user is not done inputting data
				*pcpsiOptionalStatusIcon = CPSI_ERROR;
				hr = SHStrDupW(L"Nyt kodeord og gentag kodeord er ikke ens", ppwszOptionalStatusText);
			}
		}
		else
		{
			// 0 length passwords
			*pcpgsr = CPGSR_NO_CREDENTIAL_NOT_FINISHED;
			*pcpsiOptionalStatusIcon = CPSI_ERROR;
			hr = SHStrDupW(L"Kodeordet må ikke være tomt", ppwszOptionalStatusText);
		}
	}
	else if (_cpus == CPUS_LOGON || _cpus == CPUS_UNLOCK_WORKSTATION)
	{
		// In the case of a login we will use the default serilization object
		if (_fIsLocalUser || _IsAnonUser)
		{
			// We handle local users and anonymous users almost the same way
			// they do differ in how to fetch the username and domain
			if (_IsLoggingEnabled)
			{
				LOG_F(1, "GetSerialization: Local or Anonymous user");
			}

			PWSTR pwzProtectedPassword;
			hr = ProtectIfNecessaryAndCopyPassword(_rgFieldStrings[FI_PASSWORD], _cpus, &pwzProtectedPassword);
			if (SUCCEEDED(hr))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(1, "GetSerialization: Password copied");
				}

				PWSTR pszDomain{};
				PWSTR pszUsername{};

				// Get Username and domain
				// Logic for getting them is slighty different depending on if its a local user or a anonymous user
				if (_IsAnonUser)
				{
					// For Anonymous users we first check if the user supplied a domain in the username field, if they did we will just use that to determine domain
					// if the user only provided a username however, we need to fetch domain the machine we are running on is joined to and use that instead

					// check to see if we have an '\' character in the username indicating a domain has been supplied
					const wchar_t* positionOfSplitter = wcschr(_rgFieldStrings[FI_USERNAME], L'\\');
					if (positionOfSplitter != nullptr)
					{
						// A domain has been supplied, so try to extract it
						hr = SplitDomainAndUsername(_rgFieldStrings[FI_USERNAME], &pszDomain, &pszUsername);
					}
					else {
						// No domain was supplied, so we default to the domain the machine is joined to and try to login with that
						PWKSTA_INFO_100 info;
						hr = NetWkstaGetInfo(NULL, 100, (LPBYTE*)&info);
						if (SUCCEEDED(hr))
						{
							if (_IsLoggingEnabled)
							{
								LOG_F(1, "GetSerialization: NetWkstaGetInfo fetched");
							}

							hr = SHStrDupW(info->wki100_langroup, &pszDomain);

							if (SUCCEEDED(hr))
							{
								if (_IsLoggingEnabled)
								{
									LOG_F(1, "GetSerialization: Domain copied to variable");
								}

								// Try to fetch and cache UPN/SAMAccountName keypair by username
								if (_IsUPNCacheEnabled) {
									_FetchAndSaveUPN(_rgFieldStrings[FI_USERNAME]);
								}

								// Figure out if username is of type sAMAccountName or UPN
								const wchar_t* positionOfAtSign = wcschr(_rgFieldStrings[FI_USERNAME], L'@');
								PWSTR sAMAccountName;
								if (_IsUPNCacheEnabled && positionOfAtSign != nullptr) {
									hr = _ConvertUPNToSAMAccountName(_rgFieldStrings[FI_USERNAME], sAMAccountName);
									if (SUCCEEDED(hr))
									{
										hr = SHStrDupW(sAMAccountName, &pszUsername);
									}
									else {
										hr = SHStrDupW(_rgFieldStrings[FI_USERNAME], &pszUsername);
									}
								}
								else {
									hr = SHStrDupW(_rgFieldStrings[FI_USERNAME], &pszUsername);
								}
							}
						}
						CoTaskMemFree(info);
					}
				}
				else
				{
					// For local user, the domain and user name can be split from _pszQualifiedUserName (domain\username).
					hr = SplitDomainAndUsername(_pszQualifiedUserName, &pszDomain, &pszUsername);
				}

				// CredPackAuthenticationBuffer() cannot be used because it won't work with unlock scenario for Local/anon users, 
				// so we use a different method for this than in the case of known non-local users which is handled below
				if (SUCCEEDED(hr))
				{
					if (_IsLoggingEnabled)
					{
						LOG_F(1, "GetSerialization: Username and domain copied to variable");
					}
					// In case of a Logon/Unlock we use KERB_INTERACTIVE_UNLOCK_LOGON
					KERB_INTERACTIVE_UNLOCK_LOGON kiul;
					hr = KerbInteractiveUnlockLogonInit(pszDomain, pszUsername, pwzProtectedPassword, _cpus, &kiul);
					if (SUCCEEDED(hr))
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(INFO, "GetSerialization: KerbInteractiveUnlockLogonInit OK");
						}

						// We use KERB_INTERACTIVE_UNLOCK_LOGON in both unlock and logon scenarios.  It contains a
						// KERB_INTERACTIVE_LOGON to hold the creds plus a LUID that is filled in for us by Winlogon
						// as necessary.
						hr = KerbInteractiveUnlockLogonPack(kiul, &pcpcs->rgbSerialization, &pcpcs->cbSerialization);
						if (SUCCEEDED(hr))
						{
							if (_IsLoggingEnabled)
							{
								LOG_F(INFO, "GetSerialization: KerbInteractiveUnlockLogonPack OK");
							}
							ULONG ulAuthPackage;
							hr = RetrieveNegotiateAuthPackage(&ulAuthPackage);
							if (SUCCEEDED(hr))
							{
								if (_IsLoggingEnabled)
								{
									LOG_F(INFO, "GetSerialization: RetrieveNegotiateAuthPackage OK");
								}
								pcpcs->ulAuthenticationPackage = ulAuthPackage;
								pcpcs->clsidCredentialProvider = CLSID_CSample;

								// Here we start the call to the OS2Faktor backend for verification and SSO.
								// If a success this will write a token to the registry that the browser plugins can read and use to establish a session
								_StartCreateSessionProcess(pszUsername, _rgFieldStrings[FI_PASSWORD]);

								// At this point the credential has created the serialized credential used for logon
								// By setting this to CPGSR_RETURN_CREDENTIAL_FINISHED we are letting logonUI know
								// that we have all the information we need and it should attempt to submit the
								// serialized credential.
								*pcpgsr = CPGSR_RETURN_CREDENTIAL_FINISHED;
							}
						}
					}

					CoTaskMemFree(pszDomain);
					CoTaskMemFree(pszUsername);
				}
				CoTaskMemFree(pwzProtectedPassword);
			}
		}
		else
		{
			// Non-local, Non-anonymous user
			if (_IsLoggingEnabled)
			{
				LOG_F(1, "GetSerialization: Not a local or anonymous user");
			}
			DWORD dwAuthFlags = CRED_PACK_PROTECTED_CREDENTIALS | CRED_PACK_ID_PROVIDER_CREDENTIALS;

			// First get the size of the authentication buffer to allocate
			if (!CredPackAuthenticationBuffer(dwAuthFlags, _pszQualifiedUserName, const_cast<PWSTR>(_rgFieldStrings[FI_PASSWORD]), nullptr, &pcpcs->cbSerialization) &&
				(GetLastError() == ERROR_INSUFFICIENT_BUFFER))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(1, "GetSerialization: get the size of the authentication buffer OK");
				}
				pcpcs->rgbSerialization = static_cast<byte*>(CoTaskMemAlloc(pcpcs->cbSerialization));
				if (pcpcs->rgbSerialization != nullptr)
				{
					hr = S_OK;

					// Retrieve the authentication buffer
					if (CredPackAuthenticationBuffer(dwAuthFlags, _pszQualifiedUserName, const_cast<PWSTR>(_rgFieldStrings[FI_PASSWORD]), pcpcs->rgbSerialization, &pcpcs->cbSerialization))
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(1, "GetSerialization: Retrieve the authentication buffer");
						}
						ULONG ulAuthPackage;
						hr = RetrieveNegotiateAuthPackage(&ulAuthPackage);
						if (SUCCEEDED(hr))
						{
							if (_IsLoggingEnabled)
							{
								LOG_F(1, "GetSerialization: RetrieveNegotiateAuthPackage OK");
							}
							pcpcs->ulAuthenticationPackage = ulAuthPackage;
							pcpcs->clsidCredentialProvider = CLSID_CSample;

							// We need just the username for verification in OS2Faktor so we need to split the _pszQualifiedUserName up
							PWSTR pszDomain;
							PWSTR pszUsername;
							hr = SplitDomainAndUsername(_pszQualifiedUserName, &pszDomain, &pszUsername);
							if (_IsLoggingEnabled)
							{
								LOG_F(1, "GetSerialization: SplitDomainAndUsername OK");
							}

							// Here we start the call to the OS2Faktor backend for verification and SSO.
							// If a success this will write a token to the registry that the browser plugins can read and use to establish a session
							_StartCreateSessionProcess(pszUsername, _rgFieldStrings[FI_PASSWORD]);
							if (_IsLoggingEnabled)
							{
								LOG_F(INFO, "GetSerialization: _StartCreateSessionProcess OK");
							}

							// Free the memory we just used before logging in to windows
							CoTaskMemFree(pszDomain);
							CoTaskMemFree(pszUsername);

							// At this point the credential has created the serialized credential used for logon
							// By setting this to CPGSR_RETURN_CREDENTIAL_FINISHED we are letting logonUI know
							// that we have all the information we need and it should attempt to submit the
							// serialized credential.
							*pcpgsr = CPGSR_RETURN_CREDENTIAL_FINISHED;
						}
					}
					else
					{
						hr = HRESULT_FROM_WIN32(GetLastError());
						if (SUCCEEDED(hr))
						{
							hr = E_FAIL;
						}
					}

					if (FAILED(hr))
					{
						CoTaskMemFree(pcpcs->rgbSerialization);
					}
				}
				else
				{
					hr = E_OUTOFMEMORY;
				}
			}
		}
	}

	return hr;
}

struct REPORT_RESULT_STATUS_INFO
{
	NTSTATUS ntsStatus;
	NTSTATUS ntsSubstatus;
	PWSTR     pwzMessage;
	CREDENTIAL_PROVIDER_STATUS_ICON cpsi;
};

static const REPORT_RESULT_STATUS_INFO s_rgLogonStatusInfo[] =
{
	{ STATUS_LOGON_FAILURE, STATUS_SUCCESS, L"Forkert kodeord eller brugernavn.", CPSI_ERROR, },
	{ STATUS_ACCOUNT_RESTRICTION, STATUS_ACCOUNT_DISABLED, L"Bruger er deaktiveret.", CPSI_WARNING },
};

// ReportResult is completely optional.  Its purpose is to allow a credential to customize the string
// and the icon displayed in the case of a logon failure.  For example, we have chosen to
// customize the error shown in the case of bad username/password and in the case of the account
// being disabled.
HRESULT Credential::ReportResult(NTSTATUS ntsStatus,
	NTSTATUS ntsSubstatus,
	_Outptr_result_maybenull_ PWSTR* ppwszOptionalStatusText,
	_Out_ CREDENTIAL_PROVIDER_STATUS_ICON* pcpsiOptionalStatusIcon)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "ReportResult called");
		LOG_F(INFO, "ntsStatus %X", ntsStatus);
		LOG_F(INFO, "ntsSubstatus %X", ntsSubstatus);
	}
	*ppwszOptionalStatusText = nullptr;
	*pcpsiOptionalStatusIcon = CPSI_NONE;

	DWORD dwStatusInfo = (DWORD)-1;

	// Look for a match on status and substatus.
	for (DWORD i = 0; i < ARRAYSIZE(s_rgLogonStatusInfo); i++)
	{
		if (s_rgLogonStatusInfo[i].ntsStatus == ntsStatus && s_rgLogonStatusInfo[i].ntsSubstatus == ntsSubstatus)
		{
			dwStatusInfo = i;
			break;
		}
	}

	if ((DWORD)-1 != dwStatusInfo)
	{
		if (SUCCEEDED(SHStrDupW(s_rgLogonStatusInfo[dwStatusInfo].pwzMessage, ppwszOptionalStatusText)))
		{
			*pcpsiOptionalStatusIcon = s_rgLogonStatusInfo[dwStatusInfo].cpsi;
		}
	}

	switch (_cpus)
	{
	case CPUS_LOGON:
	case CPUS_UNLOCK_WORKSTATION:
		// Check if we have previously prompted the user to change their password before logging in, 
		// if it went well send the password to backend
		if (_IsStatusPasswordMustChange)
		{
			if (SUCCEEDED(HRESULT_FROM_NT(ntsStatus)))
			{
				// No longer in password change scenario
				_IsStatusPasswordMustChange = false;

				// Split the qualifiedUsername 
				PWSTR pszDomain;
				PWSTR pszUsername;
				HRESULT hr = SplitDomainAndUsername(_pszQualifiedUserName, &pszDomain, &pszUsername);
				if (SUCCEEDED(hr) && wcslen(_rgFieldStrings[FI_NEW_PASSWORD]) > 0)
				{
					_StartChangePasswordProcess(pszUsername, _rgFieldStrings[FI_PASSWORD], _rgFieldStrings[FI_NEW_PASSWORD]);
				}

				// Override the password used for serilization and indicate that we want to autologin due to a successful password change
				_rgFieldStrings[FI_PASSWORD] = _rgFieldStrings[FI_NEW_PASSWORD];
				_IsPasswordChangeBeforeLoginSuccess = true;
			}
			else {
				// Change password before login failed, clear fields

				// If we failed the logon, try to erase the password + username fields.
				if (FAILED(HRESULT_FROM_NT(ntsStatus)))
				{
					if (_pCredProvCredentialEvents)
					{
						_pCredProvCredentialEvents->SetFieldString(this, FI_PASSWORD, L"");
						_pCredProvCredentialEvents->SetFieldString(this, FI_NEW_PASSWORD, L"");
						_pCredProvCredentialEvents->SetFieldString(this, FI_NEW_PASSWORD_CONFIRM, L"");
					}
				}
			}
			return S_OK;
		}

		// If we failed logon due to user needing to change their password before logging in, 
		// we should show new password fields
		if (ntsStatus == STATUS_PASSWORD_MUST_CHANGE || (ntsStatus == STATUS_ACCOUNT_RESTRICTION && ntsSubstatus == STATUS_PASSWORD_EXPIRED)) {
			_IsStatusPasswordMustChange = true;

			_pCredProvCredentialEvents->BeginFieldUpdates();
			_pCredProvCredentialEvents->SetFieldState(nullptr, FI_NEW_PASSWORD, CPFS_DISPLAY_IN_BOTH);
			_pCredProvCredentialEvents->SetFieldState(nullptr, FI_NEW_PASSWORD_CONFIRM, CPFS_DISPLAY_IN_BOTH);

			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_NEW_PASSWORD, CPFIS_FOCUSED);
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_PASSWORD, CPFIS_NONE);
			_pCredProvCredentialEvents->SetFieldSubmitButton(nullptr, FI_SUBMIT_BUTTON, FI_NEW_PASSWORD_CONFIRM);
			_pCredProvCredentialEvents->EndFieldUpdates();
		}
		else {
			_IsStatusPasswordMustChange = false;

			_pCredProvCredentialEvents->BeginFieldUpdates();
			_pCredProvCredentialEvents->SetFieldState(nullptr, FI_NEW_PASSWORD, CPFS_HIDDEN);
			_pCredProvCredentialEvents->SetFieldState(nullptr, FI_NEW_PASSWORD_CONFIRM, CPFS_HIDDEN);
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_NEW_PASSWORD, CPFIS_NONE);
			_pCredProvCredentialEvents->SetFieldInteractiveState(nullptr, FI_PASSWORD, CPFIS_FOCUSED);
			_pCredProvCredentialEvents->SetFieldSubmitButton(nullptr, FI_SUBMIT_BUTTON, FI_PASSWORD);
			_pCredProvCredentialEvents->EndFieldUpdates();

			// If we failed the logon, try to erase the password + username fields.
			if (FAILED(HRESULT_FROM_NT(ntsStatus)))
			{
				if (_pCredProvCredentialEvents)
				{
					_pCredProvCredentialEvents->SetFieldString(this, FI_PASSWORD, L"");
					//_pCredProvCredentialEvents->SetFieldString(this, FI_USERNAME, L"");
				}
			}
		}

		break;
	case CPUS_CHANGE_PASSWORD:
		// If we failed the changepassword, try to erase the password field.
		if (FAILED(HRESULT_FROM_NT(ntsStatus)))
		{
			if (_pCredProvCredentialEvents)
			{
				_pCredProvCredentialEvents->SetFieldString(this, FI_PASSWORD, L"");
			}
		}

		// If password change SUCCEEDED call os2faktor Login
		if (SUCCEEDED(HRESULT_FROM_NT(ntsStatus)))
		{
			PWSTR pszDomain;
			PWSTR pszUsername;
			HRESULT hr = SplitDomainAndUsername(_pszQualifiedUserName, &pszDomain, &pszUsername);
			if (SUCCEEDED(hr))
			{
				_StartChangePasswordProcess(pszUsername, _rgFieldStrings[FI_PASSWORD], _rgFieldStrings[FI_NEW_PASSWORD]);
			}
		}
		break;
	default:
		break;
	}

	// Since nullptr is a valid value for *ppwszOptionalStatusText and *pcpsiOptionalStatusIcon
	// this function can't fail.
	return S_OK;
}

// Gets the SID of the user corresponding to the credential.
HRESULT Credential::GetUserSid(_Outptr_result_nullonfailure_ PWSTR* ppszSid)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetUserSid called");
	}
	*ppszSid = nullptr;
	HRESULT hr = E_UNEXPECTED;

	// Return S_FALSE with a null SID in ppszSid for the
	// credential to be associated with an empty user tile.
	if (_IsAnonUser) {
		return S_FALSE;
	}

	if (_pszUserSid != nullptr)
	{
		hr = SHStrDupW(_pszUserSid, ppszSid);
		return hr;
	}

	return hr;
}

// GetFieldOptions to enable the password reveal button and touch keyboard auto-invoke in the password field.
HRESULT Credential::GetFieldOptions(DWORD dwFieldID,
	_Out_ CREDENTIAL_PROVIDER_CREDENTIAL_FIELD_OPTIONS* pcpcfo)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetFieldOptions called");
	}
	*pcpcfo = CPCFO_NONE;

	if (dwFieldID == FI_PASSWORD)
	{
		*pcpcfo = CPCFO_ENABLE_PASSWORD_REVEAL;
	}
	else if (dwFieldID == FI_TILEIMAGE)
	{
		*pcpcfo = CPCFO_ENABLE_TOUCH_KEYBOARD_AUTO_INVOKE;
	}

	return S_OK;
}

void mergeWChar(wchar_t*& dest, const wchar_t* source)
{
	if (dest == nullptr)
	{
		dest = const_cast<wchar_t*>(source);
		return;
	}

	size_t count = wcslen(dest) + wcslen(source) + 1;
	wchar_t* newdest = (wchar_t*)malloc(count * sizeof(wchar_t));
	wcscpy(newdest, dest);
	wcscat(newdest, source);

	dest = newdest;
}

void Credential::_StartCreateSessionProcess(PWSTR username, PWSTR password)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_StartCreateSessionProcess called");
	}

	try
	{
		// Setup STARTUPINFO and PROCESS_INFORMATION for process creation
		STARTUPINFO si;
		ZeroMemory(&si, sizeof(si));
		si.cb = sizeof(si);

		PROCESS_INFORMATION pi;
		ZeroMemory(&pi, sizeof(pi));

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Memory for new process setup");
		}

		// Fetch location of createSession from registry
		wstring installPath;
		LONG getValueResult = GetStringRegKey(_hKey, L"InstallPath", installPath, L"C:/Program Files/Digital Identity/OS2faktorLogin CredentialProvider");
		installPath.append(L"/CreateSession/CreateSession.exe");

		// Construct commandline string
		wchar_t* commandline = nullptr;
		mergeWChar(commandline, L"\"");
		mergeWChar(commandline, installPath.c_str());
		mergeWChar(commandline, L"\" ");
		mergeWChar(commandline, username);
		mergeWChar(commandline, L" ");
		mergeWChar(commandline, password);

		// Start C# process
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "Starting CreateProcess");
		}
		try
		{
			if (!CreateProcess(
				NULL,
				commandline,
				NULL,
				NULL,
				NULL,
				CREATE_NO_WINDOW,
				NULL,
				NULL,
				&si,
				&pi
			))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(ERROR, "CreateProcess failed (%d)", GetLastError());
				}
				return;
			}
		}
		catch (const std::exception&)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "CreateProcess threw an exception(%d)", GetLastError());
			}
			return;
		}
		// Close process and thread handles. 
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Closing handles");
		}

		SecureZeroMemory(commandline, sizeof(commandline));
		free(commandline);
		CloseHandle(pi.hProcess);
		CloseHandle(pi.hThread);

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Handles closed");
		}
		return;
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "_StartCreateSessionProcess threw an exception(%d)", GetLastError());
		}
	}
}

void Credential::_StartChangePasswordProcess(PWSTR username, PWSTR oldPassword, PWSTR newPassword) {
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_StartChangePasswordProcess called");
	}

	try
	{
		// Setup STARTUPINFO and PROCESS_INFORMATION for process creation
		STARTUPINFO si;
		ZeroMemory(&si, sizeof(si));
		si.cb = sizeof(si);

		PROCESS_INFORMATION pi;
		ZeroMemory(&pi, sizeof(pi));

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Memory for new process setup");
		}

		// Fetch location of createSession from registry
		wstring installPath;
		LONG getValueResult = GetStringRegKey(_hKey, L"InstallPath", installPath, L"C:/Program Files/Digital Identity/OS2faktorLogin CredentialProvider");
		installPath.append(L"/ChangePassword/ChangePassword.exe");

		// Construct commandline string
		wchar_t* commandline = nullptr;
		mergeWChar(commandline, L"\"");
		mergeWChar(commandline, installPath.c_str());
		mergeWChar(commandline, L"\" \"");
		mergeWChar(commandline, username);
		mergeWChar(commandline, L"\" \"");
		mergeWChar(commandline, oldPassword);
		mergeWChar(commandline, L"\" \"");
		mergeWChar(commandline, newPassword);
		mergeWChar(commandline, L"\"");

		// Start C# process
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "Starting CreateProcess");
		}
		try
		{
			if (!CreateProcess(
				NULL,
				commandline,
				NULL,
				NULL,
				NULL,
				CREATE_NO_WINDOW,
				NULL,
				NULL,
				&si,
				&pi
			))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(ERROR, "CreateProcess failed (%d)", GetLastError());
				}
				return;
			}
		}
		catch (const std::exception&)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "CreateProcess threw an exception(%d)", GetLastError());
			}
			return;
		}
		// Close process and thread handles. 
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Closing handles");
		}

		SecureZeroMemory(commandline, sizeof(commandline));
		free(commandline);
		CloseHandle(pi.hProcess);
		CloseHandle(pi.hThread);

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Handles closed");
		}
		return;
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "_StartChangePasswordProcess threw an exception(%d)", GetLastError());
		}
	}
}

void Credential::_StartResetPasswordProcess()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_StartResetPasswordProcess called");
	}

	try
	{
		// Setup STARTUPINFO and PROCESS_INFORMATION for process creation
		STARTUPINFO si;
		ZeroMemory(&si, sizeof(si));
		si.cb = sizeof(si);

		PROCESS_INFORMATION pi;
		ZeroMemory(&pi, sizeof(pi));

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Memory for new process setup");
		}

		// Fetch location of ResetPasswordDialog from registry
		wstring installPath;
		LONG getValueResult = GetStringRegKey(_hKey, L"InstallPath", installPath, L"C:\\Program Files\\Digital Identity\\OS2faktorLogin CredentialProvider");

		// Construct working dir
		installPath.append(L"\\ResetPassword");
		wchar_t* workingDir = nullptr;
		mergeWChar(workingDir, L"");
		mergeWChar(workingDir, installPath.c_str());

		// Construct commandline string
		installPath.append(L"\\ResetPasswordDialog.exe");
		wchar_t* commandline = nullptr;
		mergeWChar(commandline, L"\"");
		mergeWChar(commandline, installPath.c_str());
		mergeWChar(commandline, L"\"");

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "commandline: %ws", commandline);
			LOG_F(1, "workingDir: %ws", workingDir);
			LOG_F(INFO, "Starting CreateProcess");
		}
		// Start C# process
		try
		{
			if (!CreateProcess(
				NULL,
				commandline,
				NULL,
				NULL,
				NULL,
				0,
				NULL,
				workingDir,
				&si,
				&pi
			))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(ERROR, "CreateProcess failed (%d)", GetLastError());
				}
				return;
			}
		}
		catch (const std::exception&)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "CreateProcess threw an exception(%d)", GetLastError());
			}
			return;
		}

		// Close process and thread handles. 
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Closing handles");
		}

		SecureZeroMemory(commandline, sizeof(commandline));
		free(commandline);
		CloseHandle(pi.hProcess);
		CloseHandle(pi.hThread);

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Handles closed");
		}
		return;
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "_StartCreateSessionProcess threw an exception(%d)", GetLastError());
		}
	}
}


HRESULT Credential::_ConvertUPNToSAMAccountName(PWSTR upn, _Outref_result_nullonfailure_ PWSTR &sAMAccountName)
{
	LOG_F(INFO, "_ConvertUPNToSAMAccountName called");
	try
	{
		HKEY hKey;
		DWORD disposition;

		wchar_t* upnPath = nullptr;
		mergeWChar(upnPath, L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin\\UPNCache\\");
		mergeWChar(upnPath, upn);

		if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, upnPath, 0, KEY_ALL_ACCESS, &hKey) != ERROR_SUCCESS)
		{
			sAMAccountName = L"";
			return GetLastError();
		}

		wstring keyValue;
		LONG getValueResult = GetStringRegKey(hKey, L"sAMAccountName", keyValue, L"");
		if (getValueResult != ERROR_SUCCESS) {
			LOG_F(ERROR, "Could not get sAMAccountName from registry. Error code: %d", GetLastError());
			sAMAccountName = L"";
			return GetLastError();
		}

		sAMAccountName = &keyValue[0];
		return S_OK;
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "ConvertUPNToSAMAccountName threw an exception(%d)", GetLastError());
		}
		sAMAccountName = L"";
		return GetLastError();
	}
}

HRESULT Credential::_FetchAndSaveUPN(PWSTR username)
{
	LOG_F(INFO, "_FetchAndSaveUPN called");
	try
	{
		// Figure out if username is of type sAMAccountName or UPN
		const wchar_t* positionOfSplitter = wcschr(username, L'@');

		PWSTR sAMAccountName;
		PWSTR userPrincipalName;

		ULONG size = 1013;
		wchar_t buffer[1013];
		if (positionOfSplitter == nullptr)
		{
			return S_OK;
		}

		BOOLEAN result = TranslateNameW(username, NameUserPrincipal, NameSamCompatible, buffer, &size);
		if (result == 0) {
			DWORD err = GetLastError();
			LOG_F(INFO, "Failed to Translate UPN error code (%d)", err);
			return err;
		}

		userPrincipalName = username;
		sAMAccountName = buffer;

		//LOG_F(1, "Convert username success. SAMAccountName: '%ws', UserPrincipalName: '%ws'", sAMAccountName, userPrincipalName);

		// Save UPN/sAMAccountName key/value pair
		HKEY hKey;
		DWORD disposition;
		wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin";
		LSTATUS lResult = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_ALL_ACCESS, &hKey);
		if (lResult == ERROR_SUCCESS)
		{
			wchar_t* upnPath = nullptr;
			mergeWChar(upnPath, L"UPNCache\\");
			mergeWChar(upnPath, userPrincipalName);
			lResult = RegCreateKeyEx(hKey, upnPath, 0, NULL, 0, KEY_ALL_ACCESS, NULL, &hKey, &disposition);
			if (lResult == ERROR_SUCCESS)
			{
				PWSTR pszDomain{};
				PWSTR pszUsername{};

				HRESULT hr = SplitDomainAndUsername(sAMAccountName, &pszDomain, &pszUsername);
				if (SUCCEEDED(hr)) {
					lResult = RegSetValueExW(hKey, L"sAMAccountName", 0, REG_SZ, (BYTE*)pszUsername, sizeof(wchar_t) * wcslen(pszUsername));
				}
			}

			return lResult;
		}
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "ConvertUPNToSAMAccountName threw an exception(%d)", GetLastError());
		}
	}
}
