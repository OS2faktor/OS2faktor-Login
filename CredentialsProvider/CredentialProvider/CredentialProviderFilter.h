#pragma once
#include <credentialprovider.h>
#include <windows.h>
#include <strsafe.h>
#include <shlguid.h>
#include <propkey.h>
#include "common.h"
#include "dll.h"
#include "resource.h"
#include "helpers.h"


class CredentialProviderFilter : public ICredentialProviderFilter
{
public:
    // IUnknown
    IFACEMETHODIMP_(ULONG) AddRef()
    {
        return ++_cRef;
    }

    IFACEMETHODIMP_(ULONG) Release()
    {
        long cRef = --_cRef;
        if (!cRef)
        {
            delete this;
        }
        return cRef;
    }

    IFACEMETHODIMP QueryInterface(_In_ REFIID riid, _COM_Outptr_ void** ppv)
    {
        static const QITAB qit[] =
        {
            QITABENT(CredentialProviderFilter, ICredentialProviderFilter), // IID_ICredentialProviderFilter
            {0},
        };
        return QISearch(this, qit, riid, ppv);
    }

public:
    CredentialProviderFilter();
    bool GetBoolValue(const HKEY& hKey, std::wstring& key, bool& default);
    HRESULT Filter(_In_ CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, _In_ DWORD dwFlags, _In_ GUID* rgclsidProviders, _Inout_ BOOL* rgbAllow, _In_ DWORD cProviders);
    HRESULT UpdateRemoteCredential(_In_ const CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION* pcpcsIn, _Out_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION* pcpcsOut);
    friend HRESULT OS2faktorProviderFilter_CreateInstance(_In_ REFIID riid, _Outptr_ void** ppv);

private:
    virtual ~CredentialProviderFilter();
    long _cRef;
    bool _IsLoggingEnabled;
    bool _ShouldFilterLogon;
    bool _ShouldFilterUnlockWorkstation;
    bool _ShouldFilterChangePassword;
    bool _ShouldFilterCredUi;
    bool _ShouldFilterPlap;
    HKEY _hKey;
};

