module IssuesHelper
  PRIORITY_CLASSES = {
    "urgent" => "bg-red-100 text-red-800",
    "high"   => "bg-orange-100 text-orange-800",
    "medium" => "bg-yellow-100 text-yellow-800",
    "low"    => "bg-green-100 text-green-800"
  }.freeze

  STATUS_CLASSES = {
    "backlog"     => "bg-gray-100 text-gray-700",
    "todo"        => "bg-blue-100 text-blue-700",
    "in_progress" => "bg-purple-100 text-purple-700",
    "review"      => "bg-yellow-100 text-yellow-700",
    "done"        => "bg-green-100 text-green-700"
  }.freeze

  def priority_class(priority)
    PRIORITY_CLASSES.fetch(priority, "bg-gray-100 text-gray-800")
  end

  def status_class(status)
    STATUS_CLASSES.fetch(status, "bg-gray-100 text-gray-700")
  end

  def status_label(status)
    t("issues.statuses.#{status}", default: status.humanize)
  end
end
