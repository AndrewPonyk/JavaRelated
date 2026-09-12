class User < ApplicationRecord
  devise :database_authenticatable, :registerable, :recoverable,
         :rememberable, :validatable, :trackable, :omniauthable,
         omniauth_providers: [:google_oauth2]

  has_many :memberships, dependent: :destroy
  has_many :projects, through: :memberships
  has_many :assigned_issues, class_name: "Issue", foreign_key: :assignee_id,
                             inverse_of: :assignee, dependent: :nullify
  has_many :reported_issues, class_name: "Issue", foreign_key: :reporter_id,
                             inverse_of: :reporter, dependent: :restrict_with_error
  has_many :time_entries, dependent: :destroy
  has_many :comments, dependent: :destroy

  validates :name, presence: true, length: { maximum: 120 }

  has_secure_token :api_token

  def display_name
    name.presence || email.split("@").first
  end

  def admin?
    admin_flag == true
  end

  def admin_for?(project)
    memberships.find_by(project_id: project.id)&.admin?
  end
end
