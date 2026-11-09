#include "./password_filter_api.hpp"

#include "./password_filter_service.hpp"

#include <spdlog/logger.h>
#include <spdlog/spdlog.h>
#include <windows.h>

#include "./string.hpp"
#include "./logging.hpp"

// We do not have anything to initialize here, so we return true.
PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall InitializeChangeNotify(void) {
	return TRUE;
}

// A PasswordFilter is also notified of password changes,
// we do not need or use this information.
PASSWORDVALIDATIONFILTER_API int __stdcall
PasswordChangeNotify(PUNICODE_STRING UserName,
                     ULONG RelativeId,
                     PUNICODE_STRING NewPassword) {
    return 0;
}

PASSWORDVALIDATIONFILTER_API BOOLEAN __stdcall 
PasswordFilter(PUNICODE_STRING AccountName,
			   PUNICODE_STRING FullName,
               PUNICODE_STRING Password,
               BOOLEAN SetOperation) {
    try {
        auto logger = pf::setup_logger().value_or(spdlog::default_logger());
        try {
            if(Password == nullptr || Password->Buffer == nullptr) {
                logger->error("No password was provided");
                return TRUE;
            }

            if(AccountName == nullptr || AccountName->Buffer == nullptr) {
                logger->error("No account name was provided");
                return TRUE;
            }

            const auto password_filter_service = pf::PasswordFilterService::create(logger);
            if(!password_filter_service) {
                logger->error("Failed to create password filter service: {}", password_filter_service.error());
                return TRUE;
            }

            const auto result = password_filter_service->validate_password(AccountName,
                                                                           Password,
                                                                           !SetOperation);

            auto account_name = std::string{"N/A"};
            if(const auto account_name_result = pf::string::to_ansi(std::wstring{AccountName->Buffer, AccountName->Length / sizeof(wchar_t)})) {
                account_name = account_name_result.value();
            }

            if(result) {
                logger->info("Password validation succeeded for account \"{}\"", account_name);
            } else {
                logger->info("Password did not meet requirements for account \"{}\"", account_name);
            }

            return result;
        } catch(const std::exception& e) {
            logger->error("Password validation failed with exception: {}", e.what());
            throw;
        } catch(...) {
            logger->error("Password validation failed with unknown exception");
            throw;
        }
    } catch(...) {
        return TRUE;
    }
}
