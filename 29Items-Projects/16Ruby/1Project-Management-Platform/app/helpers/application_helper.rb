module ApplicationHelper
  def flash_class(level)
    case level.to_s
    when "notice", "success" then "bg-green-50 border-green-300 text-green-800"
    when "alert", "error"    then "bg-red-50 border-red-300 text-red-800"
    else "bg-gray-50 border-gray-300 text-gray-800"
    end
  end
end
