#include <Windows.h>
#include <string>
#include "loguru.hpp"
#include "sstream"
#include <locale>
#include <codecvt>
using namespace std;

// from npapi.h
#define WNNC_SPEC_VERSION                0x00000001
#define WNNC_SPEC_VERSION51              0x00050001
#define WNNC_NET_TYPE                    0x00000002
#define WNNC_START                       0x0000000C
#define WNNC_WAIT_FOR_START              0x00000001

//from ntdef.h
typedef struct _UNICODE_STRING
{
	USHORT Length;
	USHORT MaximumLength;
	PWSTR Buffer;
} UNICODE_STRING, * PUNICODE_STRING;

// from NTSecAPI.h
typedef enum _MSV1_0_LOGON_SUBMIT_TYPE
{
	MsV1_0InteractiveLogon = 2,
	MsV1_0Lm20Logon,
	MsV1_0NetworkLogon,
	MsV1_0SubAuthLogon,
	MsV1_0WorkstationUnlockLogon = 7,
	MsV1_0S4ULogon = 12,
	MsV1_0VirtualLogon = 82,
	MsV1_0NoElevationLogon = 83,
	MsV1_0LuidLogon = 84,
} MSV1_0_LOGON_SUBMIT_TYPE, * PMSV1_0_LOGON_SUBMIT_TYPE;

// from NTSecAPI.h
typedef struct _MSV1_0_INTERACTIVE_LOGON
{
	MSV1_0_LOGON_SUBMIT_TYPE MessageType;
	UNICODE_STRING LogonDomainName;
	UNICODE_STRING UserName;
	UNICODE_STRING Password;
} MSV1_0_INTERACTIVE_LOGON, * PMSV1_0_INTERACTIVE_LOGON;

LSTATUS GetStringRegKey(HKEY hKey, const std::wstring& strValueName, std::wstring& strValue)
{
	WCHAR szBuffer[512];
	DWORD dwBufferSize = sizeof(szBuffer);
	LSTATUS nError;
	nError = RegQueryValueExW(hKey, strValueName.c_str(), 0, NULL, (LPBYTE)szBuffer, &dwBufferSize);
	if (ERROR_SUCCESS == nError)
	{
		strValue = szBuffer;
	}
	return nError;
}


LSTATUS GetBoolRegKey(HKEY hKey, const std::wstring& strValueName, bool& boolValue, const bool& boolDefaultValue)
{
	boolValue = boolDefaultValue;

	DWORD dwValue = 0;
	DWORD dwDataSize = sizeof(DWORD);

	LSTATUS nError;
	nError = RegQueryValueExW(hKey, strValueName.c_str(), 0, NULL, (LPBYTE)&dwValue, &dwDataSize);
	if (ERROR_SUCCESS == nError)
	{
		if (dwValue == 1)
		{
			boolValue = true;
		}
		else if (dwValue == 0) {
			boolValue = false;
		}
	}
	return nError;
}

void mergeWChar(wchar_t*& dest, const wchar_t* source)
{
	if (dest == nullptr)
	{
		dest = const_cast<wchar_t*>(source);
		return;
	}

	size_t count = wcslen(dest) + wcslen(source) + 2;
	wchar_t* newdest = (wchar_t*)malloc(count * sizeof(wchar_t));
	if (!newdest)
	{
		return;
	}
	wcscpy_s(newdest, wcslen(dest)+2, dest);
	wcscat_s(newdest, count, source);

	dest = newdest;
}

LSTATUS OpenHkey(HKEY& hKey)
{
	LSTATUS status = -1L;
	try
	{
		wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorLogin";
		status = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
		return status;
	}
	catch (const std::exception&)
	{
		return status;
	}
}

void GetSessionToken(PUNICODE_STRING PUSUsername, PUNICODE_STRING PUSPassword, PUNICODE_STRING PUSDomain, LPWSTR *logonScript)
{
	HKEY hKey;
	LSTATUS hkeyStatus;
	hkeyStatus = OpenHkey(hKey);
	if (hkeyStatus != ERROR_SUCCESS)
	{
		return;
	}


	// INIT logging
	BOOL _IsLoggingEnabled = false;
	try
	{
		wstring logPath;
		LONG getValueResult = GetStringRegKey(hKey, L"CredentialManagerLogPath", logPath);
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
	catch (const std::exception&)
	{
		_IsLoggingEnabled = false;
	}


	wstring username(PUSUsername->Buffer, PUSUsername->Length / sizeof(WCHAR));
	wstring password(PUSPassword->Buffer, PUSPassword->Length / sizeof(WCHAR));
	wstring domain(PUSDomain->Buffer, PUSDomain->Length / sizeof(WCHAR));
	try
	{
		
		LSTATUS status;

		// Fetch location of CreateSession from registry
		wstring installPath;
		status = GetStringRegKey(hKey, L"InstallPath", installPath);
		if (status != ERROR_SUCCESS)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "Could not fetch InstallPath");
			}
			return;
		}
		installPath.append(L"/CreateSession/CreateSession.exe");

		// Fetch location of SessionEstablisher from registry
		wstring sessionEstablisherPath;
		status = GetStringRegKey(hKey, L"InstallPath", sessionEstablisherPath);
		if (status != ERROR_SUCCESS)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "Could not fetch InstallPath");
			}
			return;
		}
		sessionEstablisherPath.append(L"/SessionEstablisher/SessionEstablisher.exe");

		// Protect password for passing between applications
		DATA_BLOB DataIn;
		DATA_BLOB DataOut;
		BYTE* pbDataInput = (BYTE*)password.c_str();
		DWORD cbDataInput = sizeof(wchar_t) * (wcslen(password.c_str()));

		DataIn.pbData = pbDataInput;
		DataIn.cbData = cbDataInput;
		if (!CryptProtectData(&DataIn, NULL, NULL, NULL, NULL, CRYPTPROTECT_UI_FORBIDDEN, &DataOut))
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "GetSessionToken threw an exception(%d)", GetLastError());
			}
			return;
		}

		// Base64 Encode password
		DWORD base64EncodedLen = 0;
		CryptBinaryToString(DataOut.pbData, DataOut.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, NULL, &base64EncodedLen);

		PWSTR base64EncodedString = (PWSTR)malloc(base64EncodedLen * sizeof(WCHAR));
		CryptBinaryToString(DataOut.pbData, DataOut.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, base64EncodedString, &base64EncodedLen);

		if (!base64EncodedString)
		{
			LocalFree(DataOut.pbData);
			return;
		}

		// Assemble commandline
		wstring createSessionCommandline = L"\"";
		createSessionCommandline = createSessionCommandline + installPath + L"\" " + username + L" " + base64EncodedString + L" " + domain;
		createSessionCommandline.push_back(0); //make sure path is null-terminated

		// Start C# process
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "Starting CreateProcess");
		}
		try
		{
			// Create pipe so child process can return the session establishing URL to this process
			HANDLE hChildProcessStdOutRead = NULL;
			HANDLE hChildProcessStdOutWrite = NULL;

			SECURITY_ATTRIBUTES secAttr;
			secAttr.nLength = sizeof(SECURITY_ATTRIBUTES);
			secAttr.bInheritHandle = TRUE;  // allow to inherit handles from parent process
			secAttr.lpSecurityDescriptor = NULL;

			if (!CreatePipe(&hChildProcessStdOutRead, &hChildProcessStdOutWrite, &secAttr, 0)) {
				return;
			}
			if (!SetHandleInformation(hChildProcessStdOutRead, HANDLE_FLAG_INHERIT, 0)) {
				return;
			}

			if (_IsLoggingEnabled)
			{
				LOG_F(1, "Pipes created");
			}

			// Setup STARTUPINFO and PROCESS_INFORMATION for process creation
			STARTUPINFO si;
			ZeroMemory(&si, sizeof(si));
			si.cb = sizeof(si);
			si.hStdOutput = hChildProcessStdOutWrite;
			si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
			si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
			si.dwFlags |= STARTF_USESTDHANDLES;

			PROCESS_INFORMATION pi;
			ZeroMemory(&pi, sizeof(pi));

			if (_IsLoggingEnabled)
			{
				LOG_F(INFO, "StartupInfo and ProcessInfo created");
			}

			if (CreateProcess(NULL, &createSessionCommandline[0], NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(INFO, "Post-CreateProcess, success");
				}

				// Wait for child process to be done, 5 second timeout.
				if (WaitForSingleObject(pi.hProcess, 5 * 1000) != WAIT_OBJECT_0)
				{
					if (_IsLoggingEnabled)
					{
						LOG_F(ERROR, "WaitForSingleObject was not success");
					}
				}

				// Read the data
				if (_IsLoggingEnabled)
				{
					LOG_F(INFO, "Reading result of child process");
				}
				DWORD dwRead;
				CHAR chBuf[4096];
				BOOL bSuccess = FALSE;
				DWORD bytesAvailable = 1;

				string result;
				while (true)
				{
					if (!PeekNamedPipe(hChildProcessStdOutRead, NULL, 0, NULL, &bytesAvailable, NULL))
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(ERROR, "PeekNamedPipe failed");
						}
						break;
					}

					if (bytesAvailable == 0)
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(1, "No bytes available, stopping");
						}
						break;
					}

					if (_IsLoggingEnabled)
					{
						LOG_F(1, "Bytes available: %d", bytesAvailable);
					}

					// 4096 is the MAX number to write which fits with the underlying datamodel, dwRead tells us how much was actually written.
					bSuccess = ReadFile(hChildProcessStdOutRead, chBuf, 4096, &dwRead, NULL);
					if (!bSuccess || dwRead == 0)
					{
						break;
					}

					if (_IsLoggingEnabled)
					{
						LOG_F(1, "Bytes read: %d", dwRead);
					}
					result.append(&chBuf[0], dwRead);
					if (_IsLoggingEnabled)
					{
						LOG_F(1, "Appended buffer to result");
					}
				}
				
				// We are done with chBuf
				SecureZeroMemory(chBuf, sizeof(chBuf));

				if (result.size() != 0)
				{
					result.push_back(0);

					DWORD resultLen = MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, result.c_str(), -1, NULL, 0);
					LPWSTR resultWide = (LPWSTR)LocalAlloc(LMEM_ZEROINIT, resultLen * 2);
					MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, result.c_str(), -1, resultWide, resultLen * 2);

					// We are done with result
					SecureZeroMemory(&result, sizeof(result));

					if (!resultWide)
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(ERROR, "Could not convert token to wide string");
						}
						return;
					}
					
					// Assemble logonScript
					wstring logonScriptCommandline = L"\"";
					logonScriptCommandline.append(sessionEstablisherPath.c_str());
					logonScriptCommandline.append(L"\" \"");
					logonScriptCommandline.append(resultWide);
					logonScriptCommandline.append(L"\"");
					

					if (_IsLoggingEnabled)
					{
						LOG_F(INFO, "LogonScript created");
					}

					// We are done with resultWide
					SecureZeroMemory(&resultWide, sizeof(resultWide));

					size_t count = wcslen(logonScriptCommandline.c_str()) + 2;

					LPWSTR lpScript;
					lpScript = (LPWSTR)LocalAlloc(LMEM_ZEROINIT, count * 2);
					*logonScript = lpScript;
					if (!*logonScript)
					{
						if (_IsLoggingEnabled)
						{
							LOG_F(ERROR, "Could not LocalAlloc Commandline");
						}
						return;
					}

					wcscpy_s(*logonScript, count, logonScriptCommandline.c_str());
					if (_IsLoggingEnabled)
					{
						LOG_F(INFO, "post wcscpy_s");
					}
					// We are done with resultWide
					SecureZeroMemory(&logonScriptCommandline, sizeof(logonScriptCommandline));
				}

				if (_IsLoggingEnabled)
				{
					LOG_F(INFO, "post-ReadStream, success");
				}

				// Close process and thread handles. 
				if (_IsLoggingEnabled)
				{
					LOG_F(1, "Closing handles");
				}
				CloseHandle(pi.hProcess);
				CloseHandle(pi.hThread);
				CloseHandle(hChildProcessStdOutRead); // Not inherited by child process so we can close it ourselves

				if (_IsLoggingEnabled)
				{
					LOG_F(1, "Handles closed");
				}
			}
			else 
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(ERROR, "CreateProcess failed (%d)", GetLastError());
				}
			}
		}
		catch (const std::exception&)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "CreateProcess threw an exception(%d)", GetLastError());
			}
		}

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Cleanup started");
		}

		LocalFree(DataOut.pbData);
		SecureZeroMemory(&createSessionCommandline, sizeof(createSessionCommandline));

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Cleanup finished");
		}

		return;
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "GetSessionToken threw an exception(%d)", GetLastError());
		}
	}

	SecureZeroMemory(&username, sizeof(username));
	SecureZeroMemory(&password, sizeof(password));
	SecureZeroMemory(&domain, sizeof(domain));
	RegCloseKey(hKey);
}




void ChangePassword(PUNICODE_STRING PUSUsername, PUNICODE_STRING PUSOldPassword, PUNICODE_STRING PUSNewPassword) {
	HKEY hKey;
	LSTATUS hkeyStatus;
	hkeyStatus = OpenHkey(hKey);
	if (hkeyStatus != ERROR_SUCCESS)
	{
		return;
	}

	// INIT logging
	BOOL _IsLoggingEnabled = false;
	try
	{
		wstring logPath;
		LONG getValueResult = GetStringRegKey(hKey, L"CredentialManagerLogPath", logPath);
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
	catch (const std::exception&)
	{
		_IsLoggingEnabled = false;
	}

	wstring username(PUSUsername->Buffer, PUSUsername->Length / sizeof(WCHAR));
	wstring oldPassword(PUSOldPassword->Buffer, PUSOldPassword->Length / sizeof(WCHAR));
	wstring newPassword(PUSNewPassword->Buffer, PUSNewPassword->Length / sizeof(WCHAR));
	try
	{
		LSTATUS status;

		// Fetch location of createSession from registry
		wstring installPath;
		status = GetStringRegKey(hKey, L"InstallPath", installPath);
		if (status != ERROR_SUCCESS)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "Could not fetch InstallPath");
			}
			return;
		}
		installPath.append(L"/ChangePassword/ChangePassword.exe");

		// Protect Old password for passing between applications
		DATA_BLOB DataInOld;
		DATA_BLOB DataOutOld;
		BYTE* pbDataInputOld = (BYTE*)oldPassword.c_str();
		DWORD cbDataInputOld = sizeof(wchar_t) * (wcslen(oldPassword.c_str()));

		DataInOld.pbData = pbDataInputOld;
		DataInOld.cbData = cbDataInputOld;
		if (!CryptProtectData(&DataInOld, NULL, NULL, NULL, NULL, CRYPTPROTECT_UI_FORBIDDEN, &DataOutOld))
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "ChangePassword threw an exception(%d)", GetLastError());
			}
			return;
		}

		DWORD base64EncodedLenOld = 0;
		CryptBinaryToString(DataOutOld.pbData, DataOutOld.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, NULL, &base64EncodedLenOld);

		PWSTR base64EncodedStringOld = (PWSTR)malloc(base64EncodedLenOld * sizeof(WCHAR));
		CryptBinaryToString(DataOutOld.pbData, DataOutOld.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, base64EncodedStringOld, &base64EncodedLenOld);

		if (!base64EncodedStringOld)
		{
			LocalFree(DataOutOld.pbData);
			return;
		}

		// Protect New password for passing between applications
		DATA_BLOB DataInNew;
		DATA_BLOB DataOutNew;
		BYTE* pbDataInputNew = (BYTE*)newPassword.c_str();
		DWORD cbDataInputNew = sizeof(wchar_t) * (wcslen(newPassword.c_str()));

		DataInNew.pbData = pbDataInputNew;
		DataInNew.cbData = cbDataInputNew;
		if (!CryptProtectData(&DataInNew, NULL, NULL, NULL, NULL, CRYPTPROTECT_UI_FORBIDDEN, &DataOutNew))
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "ChangePassword threw an exception(%d)", GetLastError());
			}
			return;
		}

		DWORD base64EncodedLenNew = 0;
		CryptBinaryToString(DataOutNew.pbData, DataOutNew.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, NULL, &base64EncodedLenNew);

		PWSTR base64EncodedStringNew = (PWSTR)malloc(base64EncodedLenNew * sizeof(WCHAR));
		CryptBinaryToString(DataOutNew.pbData, DataOutNew.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, base64EncodedStringNew, &base64EncodedLenNew);

		if (!base64EncodedStringNew)
		{
			LocalFree(DataOutNew.pbData);
			return;
		}

		// Assemble commandline
		wstring commandline = L"\"";
		commandline = commandline + installPath + L"\" \"" + username + L"\" \"" + base64EncodedStringOld + L"\" \"" + base64EncodedStringNew + L"\"";
		commandline.push_back(0); //make sure path is null-terminated

		// Start C# process
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "Starting CreateProcess");
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
				LOG_F(INFO, "StartupInfo and ProcessInfo created");
			}

			if (CreateProcess(NULL, &commandline[0], NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi))
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(INFO, "Post-CreateProcess, success");
				}

				// Wait for child process to be done, 5 second timeout.
				if (WaitForSingleObject(pi.hProcess, 5 * 1000) != WAIT_OBJECT_0)
				{
					if (_IsLoggingEnabled)
					{
						LOG_F(ERROR, "WaitForSingleObject was not success");
					}
				}
				

				// Close process and thread handles. 
				if (_IsLoggingEnabled)
				{
					LOG_F(1, "Closing handles");
				}
				CloseHandle(pi.hProcess);
				CloseHandle(pi.hThread);

				if (_IsLoggingEnabled)
				{
					LOG_F(1, "Handles closed");
				}
			}
			else
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(ERROR, "CreateProcess failed (%d)", GetLastError());
				}
			}
		}
		catch (const std::exception&)
		{
			if (_IsLoggingEnabled)
			{
				LOG_F(ERROR, "CreateProcess threw an exception(%d)", GetLastError());
			}
		}

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Cleanup started");
		}

		SecureZeroMemory(&commandline, sizeof(commandline));
		LocalFree(DataOutOld.pbData);
		LocalFree(DataOutNew.pbData);

		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Cleanup finished");
		}

		return;
	}
	catch (const std::exception&)
	{
		if (_IsLoggingEnabled)
		{
			LOG_F(ERROR, "GetSessionToken threw an exception(%d)", GetLastError());
		}
	}

	SecureZeroMemory(&username, sizeof(username));
	SecureZeroMemory(&oldPassword, sizeof(oldPassword));
	SecureZeroMemory(&newPassword, sizeof(newPassword));
	RegCloseKey(hKey);
}

extern "C" {
	__declspec(dllexport)
	DWORD
	APIENTRY
	NPGetCaps(DWORD nIndex)
	{
		switch (nIndex)
		{
		case WNNC_SPEC_VERSION:
			return WNNC_SPEC_VERSION51;

		case WNNC_NET_TYPE:
			return WNNC_CRED_MANAGER;

		case WNNC_START:
			return WNNC_WAIT_FOR_START;

		default:
			return 0;
		}
	}

	__declspec(dllexport)
	DWORD
	APIENTRY
	NPLogonNotify(
		PLUID lpLogonId,
		LPCWSTR lpAuthInfoType,
		LPVOID lpAuthInfo,
		LPCWSTR lpPrevAuthInfoType,
		LPVOID lpPrevAuthInfo,
		LPWSTR lpStationName,
		LPVOID StationHandle,
		LPWSTR* lpLogonScript
	)
	{
		*lpLogonScript = NULL;
		GetSessionToken(
			&(((MSV1_0_INTERACTIVE_LOGON*)lpAuthInfo)->UserName),
			&(((MSV1_0_INTERACTIVE_LOGON*)lpAuthInfo)->Password),
			&(((MSV1_0_INTERACTIVE_LOGON*)lpAuthInfo)->LogonDomainName),
			lpLogonScript);

		return WN_SUCCESS;
	}

	__declspec(dllexport)
	DWORD
	APIENTRY
	NPPasswordChangeNotify(
		LPCWSTR lpAuthentInfoType,
		LPVOID lpAuthentInfo,
		LPCWSTR lpPreviousAuthentInfoType,
		LPVOID lpPreviousAuthentInfo,
		LPWSTR lpStationName,
		LPVOID StationHandle,
		DWORD dwChangeInfo
	)
	{
		ChangePassword(
			&(((MSV1_0_INTERACTIVE_LOGON*)lpAuthentInfo)->UserName),
			&(((MSV1_0_INTERACTIVE_LOGON*)lpPreviousAuthentInfo)->Password),
			&(((MSV1_0_INTERACTIVE_LOGON*)lpAuthentInfo)->Password));

		return WN_SUCCESS;
	}
}
