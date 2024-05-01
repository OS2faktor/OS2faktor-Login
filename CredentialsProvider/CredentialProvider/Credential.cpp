//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
//

#pragma comment(lib, "crypt32.lib")

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
#include <dpapi.h>
#include <wincrypt.h>
using namespace std;



// A credential only references one user (or NULL) 
// The other important class CredentialProvider will create a credential when we know which user (or NULL) that we are dealing with
Credential::Credential() :
	_cRef(1),
	_pCredProvCredentialEvents(nullptr),
	_IsLoggingEnabled(false),
	_hKey(nullptr)
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

	// Then we release the rest of the memory, this information dosnt have to be securely erased
	for (int i = 0; i < ARRAYSIZE(_rgFieldStrings); i++)
	{
		CoTaskMemFree(_rgFieldStrings[i]);
		CoTaskMemFree(_rgCredProvFieldDescriptors[i].pszLabel);
	}

	// This closes the registry key we opened when we created this credential
	RegCloseKey(_hKey);
	DllRelease();

	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "~Credential done");
	}
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

// Initializes one credential with the field information passed in.
// Here we can descide what each field contains depending on the user and usage scenario we get passed in cpus and pcpUser
HRESULT Credential::Initialize(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus,
	_In_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR const* rgcpfd,
	_In_ FIELD_STATE_PAIR const* rgfsp,
	_In_opt_ ICredentialProviderUser* pcpUser,
	_In_opt_ PCWSTR domain,
	_In_opt_ PCWSTR username,
	_In_opt_ PCWSTR password)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "Initialize called");
	}
	HRESULT hr = S_OK;

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

	// Initialize the String value of all the fields.
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "Initializing the String value of all the fields");
	}

	// Link for opening the change password dialog. The text is configurable, if no key "ResetPasswordLinkText" is present a default value will be used
	if (SUCCEEDED(hr))
	{
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

				if (SUCCEEDED(hr))
				{
					hr = SHStrDupW(resetPasswordLinkText.c_str(), &_rgFieldStrings[FI_LARGE_TEXT]);
				}
			}
			RegCloseKey(hKey);
		}
		catch (const std::exception&)
		{
			hr = SHStrDupW(L"Jeg har glemt mit kodeord", &_rgFieldStrings[FI_RESET_PASSWORD_LINK]);
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
		LOG_F(INFO, "Launching reset password dialog");
	}
	_StartResetPasswordProcess();

	*pbAutoLogon = FALSE;
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
	}
	HRESULT hr = S_OK;
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

	if (FI_TILEIMAGE == dwFieldID)
	{
		HBITMAP bitmap = LoadBitmap(HINST_THISDLL, MAKEINTRESOURCE(IDB_BITMAP1));
		if (bitmap != nullptr)
		{
			hr = S_OK;
			*phbmp = bitmap;
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
	
	hr = E_INVALIDARG;
	return hr;
}

// Sets the value of a field which can accept a string as a value.
// This is called on each keystroke when a user types into an edit field
HRESULT Credential::SetStringValue(DWORD dwFieldID, _In_ PCWSTR pwz)
{
	HRESULT hr;

	hr = E_INVALIDARG;
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

	// Since nullptr is a valid value for *ppwszOptionalStatusText and *pcpsiOptionalStatusIcon
	// this function can't fail.
	return S_OK;
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

	if (FI_TILEIMAGE == dwFieldID)
	{
		*pcpcfo = CPCFO_ENABLE_TOUCH_KEYBOARD_AUTO_INVOKE;
	}

	return S_OK;
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