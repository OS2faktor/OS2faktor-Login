#include "./request.hpp"

#include <boost/json/impl/serialize.hpp>
#include <boost/json/object.hpp>
#include <boost/container/pmr/monotonic_buffer_resource.hpp>

#include <boost/json/storage_ptr.hpp>

#include "./string.hpp"

namespace pf {
auto create_request(PUNICODE_STRING account_name,
                    PUNICODE_STRING password,
                    std::optional<std::string> version) 
    -> std::expected<std::string, NTSTATUS> {
    auto ansi_account_name = string::to_ansi(account_name);
    if(!ansi_account_name) {
        return std::unexpected(ansi_account_name.error());
    }

    auto ansi_password = string::to_ansi(password);
    if(!ansi_password) {
        return std::unexpected(ansi_password.error());
    }

    unsigned char arena[4096];
    boost::container::pmr::monotonic_buffer_resource resource(arena, sizeof(arena));

    boost::json::storage_ptr sp(&resource);

    boost::json::object body {sp};

    body["accountName"] = ansi_account_name.value();
    body["password"] = ansi_password.value();
    if(version) {
        body["version"] = version.value();
    }

    auto result = boost::json::serialize(body, {});

    resource.release();

    SecureZeroMemory(arena, sizeof(arena));
    SecureZeroMemory(ansi_account_name->data(), ansi_account_name->size());
    SecureZeroMemory(ansi_password->data(), ansi_password->size());

    return result;
}
}
