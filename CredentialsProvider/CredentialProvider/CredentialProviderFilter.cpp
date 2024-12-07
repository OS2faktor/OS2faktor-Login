#include "CredentialProviderFilter.h"
#include "loguru.hpp"
#include <codecvt>
#include <comdef.h>
#include "guid.h"

using namespace std;

CredentialProviderFilter::CredentialProviderFilter():
	_cRef(1),
	_IsLoggingEnabled(false),
	_ShouldFilterLogon(false),
	_ShouldFilterUnlockWorkstation(false),
	_ShouldFilterChangePassword(false),
	_ShouldFilterCredUi(false),
	_ShouldFilterPlap(false),
	_hKey(nullptr)
{
	DllAddRef();

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

		// Filter logic
		subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin\\Filter";
		lResult = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
		if (lResult == ERROR_SUCCESS)
		{
			_hKey = hKey;

			bool default = false;
			_ShouldFilterLogon = GetBoolValue(hKey, (wstring)L"FilterLogon", default);
			_ShouldFilterUnlockWorkstation = GetBoolValue(hKey, (wstring)L"FilterUnlockWorkstation", default);
			_ShouldFilterChangePassword = GetBoolValue(hKey, (wstring)L"FilterChangePassword", default);
			_ShouldFilterCredUi = GetBoolValue(hKey, (wstring)L"FilterCredUi", default);
			_ShouldFilterPlap = GetBoolValue(hKey, (wstring)L"FilterPlap", default);
		}

	}
	catch (const std::exception&)
	{
		_IsLoggingEnabled = false;
	}
}

bool CredentialProviderFilter::GetBoolValue(const HKEY& hKey, std::wstring& key, bool& default)
{
	bool value;
	LONG getValueResult = GetBoolRegKey(hKey, key, value, default);
	if (getValueResult == ERROR_SUCCESS)
	{
		return value;
	}
	return default;
}

CredentialProviderFilter::~CredentialProviderFilter()
{
	if (_IsLoggingEnabled)
	{
		LOG_F(1, "~CredentialProviderFilter called");
	}

	// This closes the registry key we opened when we created this credential
	RegCloseKey(_hKey);
	DllRelease();

	if (_IsLoggingEnabled)
	{
		LOG_F(1, "~CredentialProviderFilter done");
	}
}

HRESULT CredentialProviderFilter::Filter(_In_ CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, _In_ DWORD dwFlags, _In_ GUID* rgclsidProviders, _Inout_ BOOL* rgbAllow, _In_ DWORD cProviders)
{
	// We have 2 arrays, one with is a list of CLSIDs of Credential providers, 
	// and one is a corrosponding list of true/false flags indicating if the provider can be used
	// cProviders is the number of members of the two
	
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "Filter called with usage scenario: %d", cpus);
	}

	GUID* clsid = rgclsidProviders;
	BOOL* allowed = rgbAllow;
	GUID* lastElement = rgclsidProviders + (cProviders - 1);
	int count = 0;
	while (clsid <= lastElement)
	{		
		HKEY hKey;
		wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin\\Filter\\";
		
		LPOLESTR clsidStr;
		HRESULT hr = StringFromCLSID(*clsid, &clsidStr);
		if (FAILED(hr))
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "Could not convert from CLSID to string");
			}

			++clsid;
			++allowed;
			continue;
		}

		subKey = subKey + clsidStr;
		LSTATUS lResult = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
		if (lResult == ERROR_SUCCESS)
		{
			_hKey = hKey;

			bool shouldFilter = false;
			bool default = false;
			switch (cpus)
			{
			case CPUS_LOGON:
				shouldFilter = GetBoolValue(hKey, (wstring)L"FilterLogon", default);
				break;
			case CPUS_UNLOCK_WORKSTATION:
				shouldFilter = GetBoolValue(hKey, (wstring)L"FilterUnlockWorkstation", default);
				break;
			case CPUS_CHANGE_PASSWORD:
				shouldFilter = GetBoolValue(hKey, (wstring)L"FilterChangePassword", default);
				break;
			case CPUS_CREDUI:
				shouldFilter = GetBoolValue(hKey, (wstring)L"FilterCredUi", default);
				break;
			case CPUS_PLAP:
				shouldFilter = GetBoolValue(hKey, (wstring)L"FilterPlap", default);
				break;
			default:
				break;
			}

			if (!shouldFilter)
			{
				++clsid;
				++allowed;
				continue;
			}

			if (_IsLoggingEnabled)
			{
				LOG_F(INFO, "%ls was filtered out for scenario %d", clsidStr, cpus);
			}


			++count;
			*allowed = false; // Set the CredentialsProvider to be filtered
		}

		// Increment both arrays
		++clsid;
		++allowed;
	}

	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "Filtering complete. %d CredentialProviders were filtered out", count);
	}

	// ALWAYS returns S_OK
	return S_OK;
}

HRESULT CredentialProviderFilter::UpdateRemoteCredential(_In_ const CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION* pcpcsIn, _Out_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION* pcpcsOut)
{
	if (_IsLoggingEnabled)
	{
		LOG_F(INFO, "UpdateRemoteCredential called");
	}

	if (!pcpcsIn)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "UpdateRemoteCredential: No Credentials Serialized");
		}
		UNREFERENCED_PARAMETER(pcpcsIn);
		UNREFERENCED_PARAMETER(pcpcsOut);
		return E_NOTIMPL;
	}

	// Copy the contents of pcpcsIn to pcpcsOut if there is anything to copy
	if (pcpcsIn->cbSerialization > 0 && (pcpcsOut->rgbSerialization = (BYTE*)CoTaskMemAlloc(pcpcsIn->cbSerialization)) != NULL)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "UpdateRemoteCredential: Credential serialization found");
		}
		pcpcsOut->ulAuthenticationPackage = pcpcsIn->ulAuthenticationPackage;
		pcpcsOut->cbSerialization = pcpcsIn->cbSerialization;
		pcpcsOut->clsidCredentialProvider = pcpcsIn->clsidCredentialProvider;

		CopyMemory(pcpcsOut->rgbSerialization, pcpcsIn->rgbSerialization, pcpcsIn->cbSerialization);

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "UpdateRemoteCredential: Credential passed to out variable");
		}
		return S_OK;
	}
	else
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "UpdateRemoteCredential: There was a problem copying the Credential");
		}
		UNREFERENCED_PARAMETER(pcpcsIn);
		UNREFERENCED_PARAMETER(pcpcsOut);
		return E_NOTIMPL;
	}
}

HRESULT OS2faktorProviderFilter_CreateInstance(_In_ REFIID riid, _Outptr_ void** ppv)
{
	HRESULT hr;
	CredentialProviderFilter* pProviderFilter = new(std::nothrow) CredentialProviderFilter();
	if (pProviderFilter)
	{
		hr = pProviderFilter->QueryInterface(riid, ppv);
		pProviderFilter->Release();
	}
	else
	{
		hr = E_OUTOFMEMORY;
	}

	return hr;
}
