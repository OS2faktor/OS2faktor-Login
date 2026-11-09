#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_LOGGING_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_LOGGING_HPP

#include <memory>
#include <spdlog/spdlog.h>

#include <expected>

namespace pf {
auto setup_logger() -> std::expected<std::shared_ptr<spdlog::logger>, std::string>;
}

#endif
