#define PASSWORDVALIDATIONFILTER_API __declspec(dllexport)

//from ntdef.h
typedef struct _UNICODE_STRING
{
	USHORT Length;
	USHORT MaximumLength;
	PWSTR Buffer;
} UNICODE_STRING, * PUNICODE_STRING;

// We do not have anything to initialize here, so we return true.
extern "C" PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall InitializeChangeNotify(void) {
	return TRUE;
}

// The actual filtering method
extern "C" PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall PasswordFilter(
	PUNICODE_STRING AccountName,
	PUNICODE_STRING FullName,
	PUNICODE_STRING Password,
	BOOLEAN SetOperation);

// A PasswordFilter is also notified of password changes, we do not need or use this information.
extern "C" PASSWORDVALIDATIONFILTER_API int __stdcall
PasswordChangeNotify(
	PUNICODE_STRING * UserName,
	ULONG RelativeId,
	PUNICODE_STRING * NewPassword) {
	SecureZeroMemory(NewPassword, sizeof(NewPassword)); 
	return 0;
}