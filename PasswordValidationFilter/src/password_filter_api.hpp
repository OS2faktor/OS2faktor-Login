#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_API_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_API_HPP

#include "./export.hpp"

#include <windows.h>
#include <winternl.h>

extern "C" {
PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall InitializeChangeNotify(void);

PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall
    PasswordFilter(PUNICODE_STRING AccountName,
                   PUNICODE_STRING FullName,
                   PUNICODE_STRING Password,
                   BOOLEAN SetOperation);

PASSWORDVALIDATIONFILTER_API int __stdcall
PasswordChangeNotify(PUNICODE_STRING UserName,
                     ULONG RelativeId,
                     PUNICODE_STRING NewPassword);
}

#endif
