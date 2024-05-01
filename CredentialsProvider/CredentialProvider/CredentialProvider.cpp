//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// CSampleProvider implements ICredentialProvider, which is the main
// interface that logonUI uses to decide which tiles to display.
// In this sample, we will display one tile that uses each of the nine
// available UI controls.

#include <initguid.h>
#include "CredentialProvider.h"
#include "CredentialProviderFilter.h"
#include "Credential.h"
#include "guid.h"
#include "loguru.hpp"
#include <locale>
#include <codecvt>
using namespace std;

CredentialProvider::CredentialProvider() :
	_cRef(1),
	_pCredProviderUserArray(nullptr),
	_IsLoggingEnabled(false),
	_hKey(nullptr),
	_externalSerializedCredential(nullptr),
	_cpus(CPUS_INVALID),
	_fRecreateEnumeratedCredentials(false),
	_shouldAutoSubmitSerializedCredential(false)
{

	try
	{
		HKEY hKey;
		wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin";
		LSTATUS lResult = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
		if (lResult == ERROR_SUCCESS)
		{
			_hKey = hKey;

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

	DllAddRef();
}

CredentialProvider::~CredentialProvider()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "~CredentialProvider called");
	}

	if (&_credentials != nullptr)
	{
		for (size_t i = 0; i < _credentials.size(); i++)
		{
			_credentials[i]->Release();
		}

		_credentials.clear();
	}

	ZeroMemory(&_credentials, sizeof(_credentials));

	CleanupSetSerialization();

	if (_pCredProviderUserArray != nullptr)
	{
		_pCredProviderUserArray->Release();
		_pCredProviderUserArray = nullptr;
	}

	DllRelease();

	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "~CredentialProvider done");
	}
}

void CredentialProvider::CleanupSetSerialization()
{
	if (_externalSerializedCredential)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "CleanupSetSerialization called");
		}

		KERB_INTERACTIVE_LOGON* pkil = &_externalSerializedCredential->Logon;
		SecureZeroMemory(_externalSerializedCredential,
			sizeof(*_externalSerializedCredential) +
			pkil->LogonDomainName.MaximumLength +
			pkil->UserName.MaximumLength +
			pkil->Password.MaximumLength
		);
		HeapFree(GetProcessHeap(), 0, _externalSerializedCredential);

		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "CleanupSetSerialization done");
		}
	}
}

// SetUsageScenario is the provider's cue that it's going to be asked for tiles
// in a subsequent call.
HRESULT CredentialProvider::SetUsageScenario(
	CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus,
	DWORD /*dwFlags*/)
{
	HRESULT hr;
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "SetUsageScenario called");
	}

	// Decide which scenarios to support here. Returning E_NOTIMPL simply tells the caller
	// that we're not designed for that scenario.

	switch (cpus)
	{
	case CPUS_LOGON:
	case CPUS_UNLOCK_WORKSTATION:
		// The reason why we need _fRecreateEnumeratedCredentials is because ICredentialProviderSetUserArray::SetUserArray() is called after ICredentialProvider::SetUsageScenario(),
		// while we need the ICredentialProviderUserArray during enumeration in ICredentialProvider::GetCredentialCount()
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "UsageScenario: %d", cpus);
		}

		_cpus = cpus;
		_fRecreateEnumeratedCredentials = true;
		hr = S_OK;
		break;
	case CPUS_CHANGE_PASSWORD:
	case CPUS_CREDUI:
		hr = E_NOTIMPL;
		break;
	default:
		hr = E_INVALIDARG;
		break;
	}

	return hr;
}

// SetSerialization takes the kind of buffer that you would normally return to LogonUI for an authentication attempt. 
// It's the opposite of ICredentialProviderCredential::GetSerialization.
// GetSerialization is implement by a credential and serializes that credential. 
// Instead, SetSerialization takes the serialization and uses it to create a tile.
//
// SetSerialization is called for two main scenarios.
// 
// The first scenario is in the credui case
//	where it is prepopulating a tile with credentials that the user chose to store in the OS.
// 
// The second situation is in a remote logon case 
//	where the remote client may wish to prepopulate a tile with a username, 
//  or in some cases, completely populate the tile and use it to logon without showing any UI.
HRESULT CredentialProvider::SetSerialization(
	_In_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION const* pcpcs)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "SetSerialization called");
	}

	HRESULT hr = E_INVALIDARG;
	return hr;
}

// Called by LogonUI to give you a callback.  Providers often use the callback if they
// some event would cause them to need to change the set of tiles that they enumerated.
HRESULT CredentialProvider::Advise(
	_In_ ICredentialProviderEvents* /*pcpe*/,
	_In_ UINT_PTR /*upAdviseContext*/)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "Advise called");
	}
	return E_NOTIMPL;
}

// Called by LogonUI when the ICredentialProviderEvents callback is no longer valid.
HRESULT CredentialProvider::UnAdvise()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "UnAdvise called");
	}
	return E_NOTIMPL;
}

// Called by LogonUI to determine the number of fields in your tiles.  This
// does mean that all your tiles must have the same number of fields.
// This number must include both visible and invisible fields. If you want a tile
// to have different fields from the other tiles you enumerate for a given usage
// scenario you must include them all in this count and then hide/show them as desired
// using the field descriptors.
HRESULT CredentialProvider::GetFieldDescriptorCount(
	_Out_ DWORD* pdwCount)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetFieldDescriptorCount called");
	}
	*pdwCount = FI_NUM_FIELDS;
	return S_OK;
}

// Gets the field descriptor for a particular field.
HRESULT CredentialProvider::GetFieldDescriptorAt(
	DWORD dwIndex,
	_Outptr_result_nullonfailure_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR** ppcpfd)
{
	HRESULT hr;
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "GetFieldDescriptorAt called");
	}
	*ppcpfd = nullptr;

	// Verify dwIndex is a valid field.
	if ((dwIndex < FI_NUM_FIELDS) && ppcpfd)
	{
		hr = FieldDescriptorCoAllocCopy(s_rgCredProvFieldDescriptors[dwIndex], ppcpfd);
	}
	else
	{
		hr = E_INVALIDARG;
	}

	return hr;
}

// Sets pdwCount to the number of tiles that we wish to show at this time.
// Sets pdwDefault to the index of the tile which should be used as the default.
// The default tile is the tile which will be shown in the zoomed view by default. If
// more than one provider specifies a default the last used cred prov gets to pick
// the default. If *pbAutoLogonWithDefault is TRUE, LogonUI will immediately call
// GetSerialization on the credential you've specified as the default and will submit
// that credential for authentication without showing any further UI.
HRESULT CredentialProvider::GetCredentialCount(
	_Out_ DWORD* pdwCount,
	_Out_ DWORD* pdwDefault,
	_Out_ BOOL* pbAutoLogonWithDefault)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "GetCredentialCount called");
	}

	// Default settings
	*pdwCount = 0; // Amount of credentials we have?
	*pdwDefault = CREDENTIAL_PROVIDER_NO_DEFAULT; // Do we have a default one?
	*pbAutoLogonWithDefault = FALSE; // Should we autologon?

	// Checking if we should recreate credentials, we set this to true when windows calls us with a usage scenario
	if (_fRecreateEnumeratedCredentials)
	{
		_fRecreateEnumeratedCredentials = false;
		_ReleaseEnumeratedCredentials();

		// This will create the credentials depending on usage scenario
		// It will create the following credential tiles:
		//	* One for each user passed to us
		//	* One for anonymous login (both username and password required)
		//  * One if we have been passed a serialized credential (From RDP)
		_CreateEnumeratedCredentials();
	}

	// Check how many users are avaliable for login and return that
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_pCredentials.size() = %d", _credentials.size());
	}

	switch (_cpus)
	{
	case CPUS_LOGON:
	case CPUS_UNLOCK_WORKSTATION:
	{
		*pdwCount = _credentials.size();
		break;
	default:
		break;
	}

	return S_OK;
	}
}

// Returns the credential at the index specified by dwIndex. This function is called by logonUI to enumerate
// the tiles.
HRESULT CredentialProvider::GetCredentialAt(
	DWORD dwIndex,
	_Outptr_result_nullonfailure_ ICredentialProviderCredential** ppcpc)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "GetCredentialAt called");
	}
	HRESULT hr = E_INVALIDARG;
	*ppcpc = nullptr;

	if ((dwIndex < _credentials.size()) && ppcpc)
	{
		hr = _credentials[dwIndex]->QueryInterface(IID_PPV_ARGS(ppcpc));
	}
	return hr;
}

void CredentialProvider::_CreateEnumeratedCredentials()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_CreateEnumeratedCredentials called");
	}
	_EnumerateCredentials();
}

void CredentialProvider::_ReleaseEnumeratedCredentials()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_ReleaseEnumeratedCredentials called");
	}

	_credentials.clear();
}

HRESULT CredentialProvider::_EnumerateCredentials()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "_EnumerateCredentials called");
	}

	HRESULT hr = E_UNEXPECTED;
	// Create additional one for anonymous login
	Credential* pCredential = new(std::nothrow) Credential();
	if (pCredential != nullptr)
	{
		hr = pCredential->Initialize(_cpus, s_rgCredProvFieldDescriptors, s_rgFieldStatePairs, nullptr, NULL, NULL, NULL);
		if (FAILED(hr))
		{
			pCredential->Release();
			pCredential = nullptr;
		}
		else {
			_credentials.push_back(pCredential);
		}
	}
	else
	{
		hr = E_OUTOFMEMORY;
	}
	return hr;
}

// Boilerplate code to create our provider.
HRESULT OS2faktorProvider_CreateInstance(_In_ REFIID riid, _Outptr_ void** ppv)
{
	HRESULT hr;
	CredentialProvider* pProvider = new(std::nothrow) CredentialProvider();
	if (pProvider)
	{
		hr = pProvider->QueryInterface(riid, ppv);
		pProvider->Release();
	}
	else
	{
		hr = E_OUTOFMEMORY;
	}

	return hr;
}
