// PasswordValidationFilter.cpp : Defines the exported functions for the DLL.

#include "pch.h"
#include "framework.h"
#include "PasswordValidationFilter.h"
#include "loguru.hpp"
#include <string>
#include <iostream>
#include <codecvt>

// Callback function for handling the HTTP response data
size_t WriteCallback(void* contents, size_t size, size_t nmemb, std::string* userp) {
    size_t totalSize = size * nmemb;
    userp->append((char*)contents, totalSize);
    return totalSize;
}


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

LSTATUS OpenHkey(HKEY& hKey)
{
	LSTATUS status = -1L;
	try
	{
		std::wstring subKey = L"SOFTWARE\\DigitalIdentity\\OS2faktorPasswordFilter";
		status = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey.c_str(), 0, KEY_READ, &hKey);
		return status;
	}
	catch (const std::exception&)
	{
		return status;
	}
}


PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall PasswordFilter(PUNICODE_STRING AccountName,
																  PUNICODE_STRING FullName,
																  PUNICODE_STRING Password,
																  BOOLEAN SetOperation) {
	BOOL _IsLoggingEnabled = false;
	try {
		HKEY hKey;
		LSTATUS hkeyStatus;
		hkeyStatus = OpenHkey(hKey);
		if (hkeyStatus != ERROR_SUCCESS)
		{
			return TRUE;
		}

		try
		{
			std::wstring logPath;
			LONG getValueResult = GetStringRegKey(hKey, L"LogPath", logPath);
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

		std::wstring username(AccountName->Buffer, AccountName->Length / sizeof(WCHAR));
		std::wstring password(Password->Buffer, Password->Length / sizeof(WCHAR));

		try
		{
			LSTATUS status;

			// Fetch location of CreateSession from registry
			std::wstring installPath;
			status = GetStringRegKey(hKey, L"InstallPath", installPath);
			if (status != ERROR_SUCCESS)
			{
				if (_IsLoggingEnabled)
				{
					LOG_F(ERROR, "Could not fetch InstallPath");
				}
				RegCloseKey(hKey);
				SecureZeroMemory(&username, sizeof(username));
				SecureZeroMemory(&password, sizeof(password));
				return TRUE;
			}
			installPath.append(L"/OS2faktorBackendCallback/OS2faktorBackendCallback.exe");

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
				RegCloseKey(hKey);
				SecureZeroMemory(&username, sizeof(username));
				SecureZeroMemory(&password, sizeof(password));
				return TRUE;
			}

			// Base64 Encode password
			DWORD base64EncodedLen = 0;
			CryptBinaryToString(DataOut.pbData, DataOut.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, NULL, &base64EncodedLen);

			PWSTR base64EncodedString = (PWSTR)malloc(base64EncodedLen * sizeof(WCHAR));
			CryptBinaryToString(DataOut.pbData, DataOut.cbData, CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, base64EncodedString, &base64EncodedLen);

			if (!base64EncodedString)
			{
				RegCloseKey(hKey);
				SecureZeroMemory(&username, sizeof(username));
				SecureZeroMemory(&password, sizeof(password));
				LocalFree(DataOut.pbData);
				return TRUE;
			}

			// Assemble commandline
			std::wstring createSessionCommandline = L"\"";
			createSessionCommandline = createSessionCommandline + installPath + L"\" " + username + L" " + base64EncodedString;
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
					RegCloseKey(hKey);
					SecureZeroMemory(&username, sizeof(username));
					SecureZeroMemory(&password, sizeof(password));
					LocalFree(DataOut.pbData);
					return TRUE;
				}
				if (!SetHandleInformation(hChildProcessStdOutRead, HANDLE_FLAG_INHERIT, 0)) {
					RegCloseKey(hKey);
					SecureZeroMemory(&username, sizeof(username));
					SecureZeroMemory(&password, sizeof(password));
					LocalFree(DataOut.pbData);
					return TRUE;
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

					std::string result;
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
						//result.push_back(0);

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
							RegCloseKey(hKey);
							SecureZeroMemory(&username, sizeof(username));
							SecureZeroMemory(&password, sizeof(password));
							LocalFree(DataOut.pbData);
							CloseHandle(pi.hProcess);
							CloseHandle(pi.hThread);
							CloseHandle(hChildProcessStdOutRead);
							return TRUE;
						}

						if (std::wcscmp(resultWide, L"-1") == 0) {
							RegCloseKey(hKey);
							SecureZeroMemory(&username, sizeof(username));
							SecureZeroMemory(&password, sizeof(password));
							LocalFree(DataOut.pbData);
							CloseHandle(pi.hProcess);
							CloseHandle(pi.hThread);
							CloseHandle(hChildProcessStdOutRead);
							SecureZeroMemory(&resultWide, sizeof(resultWide));
							return FALSE;
						}

						// We are done with resultWide
						SecureZeroMemory(&resultWide, sizeof(resultWide));
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
		
		if (_IsLoggingEnabled)
		{
			LOG_F(1, "Cleared username/password");
		}
		RegCloseKey(hKey);

		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "Closed Registry key");
		}

		// LSASS keeps this dll loaded all the time, 
		// so if we want to read any logs we need to release the log file(s).
		// This way we can actually access them on the system.
		loguru::shutdown();

		return TRUE;
	}
	catch (const std::exception& ex) {
		if (_IsLoggingEnabled)
		{
			LOG_F(INFO, "ERROR: %s", ex.what());
		}
		return TRUE;
	}
}

