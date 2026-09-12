const siteConfig = {
  name: "Northstar Travel",
  inquiryEndpoint: "/api/inquiries",
  defaultCurrency: "USD",
  supportEmail: "sales@example.com"
};

if (typeof module !== "undefined" && module.exports) {
  module.exports = { siteConfig };
}
