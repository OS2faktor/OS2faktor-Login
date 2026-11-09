#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_EVENT_VIEWER_LOG_SINK_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_EVENT_VIEWER_LOG_SINK_HPP

#include "./string.hpp"

#include <spdlog/sinks/base_sink.h>
#include <windows.h>
#include <winnt.h>

namespace pf {
constexpr wchar_t EVENT_VIEWER_PASSWORD_FILTER_LOG_NAME[] = L"DigitalIdentityOS2faktorPasswordFilterLogs";

template<typename Mutex>
class EventViewerLogSink : public spdlog::sinks::base_sink<Mutex> {
public:
    EventViewerLogSink() 
        : event_source_(RegisterEventSourceW(nullptr, EVENT_VIEWER_PASSWORD_FILTER_LOG_NAME)) {}

    ~EventViewerLogSink() {
        DeregisterEventSource(event_source_);
    }
protected:

    void sink_it_(const spdlog::details::log_msg& msg) override {
        if(event_source_ == nullptr) {
            return;
        }

        DWORD level = 0;

        switch(msg.level) {
            case spdlog::level::info:
                level = EVENTLOG_INFORMATION_TYPE;
                break;
            case spdlog::level::warn:
                level = EVENTLOG_WARNING_TYPE;
                break;
            case spdlog::level::err:
            case spdlog::level::critical:
                level = EVENTLOG_ERROR_TYPE;
                break;
            default: 
                return;
        }

        if(const auto wide_string = string::to_wide(fmt::to_string(msg.payload))) {
            auto c_str = wide_string->c_str();
            ReportEventW(event_source_, level, 0, 0, nullptr, 1, 0, &c_str, nullptr);
        }
    }

    void flush_() override {}
private:

    HANDLE event_source_;
};

using event_viewer_sink_mt = EventViewerLogSink<std::mutex>;
using event_viewer_sink_st = EventViewerLogSink<spdlog::details::null_mutex>;

}

#endif
