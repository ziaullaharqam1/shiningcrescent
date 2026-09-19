const STATUS_MESSAGES = {
  400: "Please check the details you entered and try again.",
  401: "Username or password is not correct.",
  403: "You do not have access to this.",
  404: "We could not find what you asked for.",
  409: "That action cannot be completed right now.",
  422: "Please check the details you entered and try again.",
  429: "Too many attempts. Please wait a moment and try again.",
  500: "Something went wrong on our side. Please try again.",
  502: "The service is temporarily unavailable. Please try again.",
  503: "The service is temporarily unavailable. Please try again.",
};

const CONTEXT_MESSAGES = {
  login: "Username or password is not correct.",
  otp: "We could not send or verify that code. Please try again.",
  register: "We could not create your account. Please check the details and try again.",
  cart: "We could not update your cart. Please try again.",
  checkout: "Payment could not be completed. Please try again.",
  catalog: "We could not load this right now. Please try again.",
  default: "Something went wrong. Please try again.",
};

const QUERY_ERRORS = {
  google_failed: "Google sign-in did not complete. Please try again.",
  account_disabled: "This account is disabled. Please contact support.",
  google_no_email: "Google did not share an email. Allow email access and try again.",
};

const TECHNICAL = /status code|internal server|bad request|exception|stack trace|sql|jdbc|null pointer|econnrefused|network error|axios|whitelabel|timestamp|servlet|at com\.|java\./i;

function firstString(...vals) {
  for (const v of vals) {
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return "";
}

function looksLikeTransportFailure(e) {
  if (e?.code === "ERR_NETWORK" || e?.message === "Network Error") return true;
  const data = e?.response?.data;
  if (typeof data === "string" && /proxy error|econnrefused|cannot (get|post)|<!doctype html/i.test(data.slice(0, 400))) {
    return true;
  }
  return false;
}

export function isUserFacing(msg) {
  if (!msg || typeof msg !== "string") return false;
  if (msg.length > 180) return false;
  if (TECHNICAL.test(msg)) return false;
  return true;
}

export function errMsg(e, context = "default") {
  const data = e?.response?.data;
  if (looksLikeTransportFailure(e)) {
    return "We could not reach the server. Check your connection and try again.";
  }

  const fromApi = firstString(data?.error, data?.message, data?.detail);
  if (isUserFacing(fromApi)) return fromApi;

  const status = e?.response?.status;
  if (context === "login" && (status === 400 || status === 401)) {
    return CONTEXT_MESSAGES.login;
  }
  if (STATUS_MESSAGES[status]) return STATUS_MESSAGES[status];

  const fallback = CONTEXT_MESSAGES[context] || CONTEXT_MESSAGES.default;
  if (isUserFacing(e?.message) && e.message !== fallback) {
    return e.message;
  }
  return fallback;
}

export function authQueryError(raw) {
  if (!raw) return "";
  if (QUERY_ERRORS[raw]) return QUERY_ERRORS[raw];
  if (isUserFacing(raw)) return raw;
  return QUERY_ERRORS.google_failed;
}
