using Newtonsoft.Json;
using Serilog;
using System;
using System.Dynamic;
using System.Threading.Tasks;
using WebSocketSharp;

namespace OS2faktor
{
    public class WSCommunication
    {
        private static string VERSION = "2.2.0";

        private WebSocket socket;
        private ADStub adStub;
        private ILogger logger;
        private Settings settings;
        private HMacUtil hmacUtil;

        public WSCommunication(ADStub adStub, ILogger logger, Settings settings, HMacUtil hmacUtil)
        {
            this.logger = logger;
            this.adStub = adStub;
            this.settings = settings;
            this.hmacUtil = hmacUtil;

            socket = new WebSocket(settings.GetStringValue("webSocketUrl"));
            socket.SslConfiguration.EnabledSslProtocols = System.Security.Authentication.SslProtocols.Tls12;
        }

        public void Connect()
        {
            if (!socket.IsAlive)
            {
                logger.Information("Attempting to connect");
                socket.Connect();
            }
        }

        public void Disconnect()
        {
            logger.Debug("Manual disconnect performed");
            socket.Close();
        }

        public void Init(WebSocketSharp.LogLevel logLevel = WebSocketSharp.LogLevel.Info)
        {
            socket.OnMessage += (sender, e) =>
            {
                logger.Debug("Message received");

                if (e.IsText)
                {
                    try
                    {
                        dynamic message = JsonConvert.DeserializeObject(e.Data);

                        LogRequest(message);

                        if (!ValidateMessage(message))
                        {
                            logger.Error("Invalid signature on message: " + message.transactionUuid);
                            return;
                        }

                        string command = (string)message.command;
                        switch (command)
                        {
                            case "AUTHENTICATE":
                                Reply((string)message.transactionUuid, (string)message.command, settings.GetStringValue("domain"), true);
                                break;
                            case "IS_ALIVE":
                                Reply((string)message.transactionUuid, (string)message.command, settings.GetStringValue("domain"), true, false);
                                break;
                            case "VALIDATE_PASSWORD":
                                Task.Run(() =>
                                {
                                    if (settings.GetBooleanValue("allowValidatePassword"))
                                    {
                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, adStub.ValidatePassword((string)message.target, (string)message.payload));
                                    }
                                    else
                                    {
                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                    }
                                }).ConfigureAwait(false);
                                break;
                            case "SET_PASSWORD":
                                Task.Run(() =>
                                {
                                    if (settings.GetBooleanValue("allowChangePassword"))
                                    {
                                        var result = adStub.ChangePassword((string)message.target, (string)message.payload);

                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, result.Success, true, result.Message);
                                    }
                                    else
                                    {
                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                    }
                                }).ConfigureAwait(false);
                                break;
                            case "SET_PASSWORD_WITH_FORCED_CHANGE":
                                Task.Run(() =>
                                {
                                    if (settings.GetBooleanValue("allowChangePassword"))
                                    {
                                        var result = adStub.ChangePassword((string)message.target, (string)message.payload, true);

                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, result.Success, true, result.Message);
                                    }
                                    else
                                    {
                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                    }
                                }).ConfigureAwait(false);
                                break;
                            case "UNLOCK_ACCOUNT":
                                Task.Run(() =>
                                {
                                    if (settings.GetBooleanValueWithDefault("allowUnlockAccount", true))
                                    {
                                        var result = adStub.UnlockAccount((string)message.target);

                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, result.Success, true, result.Message);
                                    }
                                    else
                                    {
                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                    }
                                }).ConfigureAwait(false);
                                break;
                            case "PASSWORD_EXPIRES_SOON":
                                Task.Run(() =>
                                {
                                    if (settings.GetBooleanValue("allowRunPasswordExpiresScript"))
                                    {
                                        var result = adStub.RunPasswordExpiresScript((string)message.target);

                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, result.Success, true, result.Message);
                                    }
                                    else
                                    {
                                        Reply((string)message.transactionUuid, (string)message.command, (string)message.target, false);
                                    }
                                }).ConfigureAwait(false);
                                break;
                            default:
                                logger.Error("Unknown request: " + message.command);
                                break;
                        }
                    }
                    catch (Exception ex)
                    {
                        logger.Error(ex, "Get error on request");
                    }
                }
                else
                {
                    logger.Warning("Got non-text message: binary=" + e.IsBinary + ", ping=" + e.IsPing);
                }
            };

            socket.OnClose += (sender, e) =>
            {
                logger.Information("Connection closed. Reason=" + e.Reason + ", code=" + e.Code);
            };

            socket.OnError += (sender, e) =>
            {
                logger.Error(e.Exception, "Error on connection: " + e.Message);
            };

            Connect();
        }

        private bool ValidateMessage(dynamic message)
        {
            string command = (string)message.command;

            switch (command)
            {
                case "AUTHENTICATE":
                case "IS_ALIVE":
                    {
                        string mac = hmacUtil.Encode(((string)message.transactionUuid + "." + (string)message.command));
                        return Equals(mac, (string)message.signature);
                    }
                case "VALIDATE_PASSWORD":
                case "SET_PASSWORD":
                case "SET_PASSWORD_WITH_FORCED_CHANGE":
                    {
                        string mac = hmacUtil.Encode(((string)message.transactionUuid + "." + (string)message.command + "." + (string)message.target + "." + (string)message.payload));
                        return Equals(mac, (string)message.signature);
                    }
                case "UNLOCK_ACCOUNT":
                case "PASSWORD_EXPIRES_SOON":
                    {
                        string mac = hmacUtil.Encode(((string)message.transactionUuid + "." + (string)message.command + "." + (string)message.target));
                        return Equals(mac, (string)message.signature);
                    }
                default:
                    logger.Information("Unknown command: " + (string) message.command);
                    break;
            }

            return false;
        }

        internal void Reply(string transactionUuid, string command, string target, bool valid, bool logResponse = true, string message = null)
        {
            dynamic response = new ExpandoObject();
            response.transactionUuid = transactionUuid;
            response.command = command;
            response.target = target;
            response.status = (valid) ? "true" : "false";
            response.clientVersion = VERSION;
            response.serverName = Environment.MachineName;
            response.signature = hmacUtil.Encode(transactionUuid + "." + command + "." + target + "." + (valid ? "true" : "false"));

            // message is not under signature - used for debugging only
            if (message != null)
            {
                response.message = message;
            }

            socket.Send(JsonConvert.SerializeObject(response));

            if (logResponse)
            {
                LogResponse(response);
            }
        }

        private void LogResponse(dynamic response)
        {
            logger.Information("Response for " + response.command + " (" + response.transactionUuid + "): target=" + response.target + ", result=" + response.status);
        }

        private void LogRequest(dynamic request)
        {
            if ("IS_ALIVE".Equals(request.command))
            {
                ; // do not log
            }
            else if ("AUTHENTICATE".Equals(request.command))
            {
                logger.Information("Request for " + request.command + " (" + request.transactionUuid + ")");
            }
            else
            {
                logger.Information("Request for " + request.command + " (" + request.transactionUuid + "): target=" + request.target);
            }
        }
    }
}
