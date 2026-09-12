class Project < ApplicationRecord
  has_many :memberships, dependent: :destroy
  has_many :users, through: :memberships
  has_many :sprints, dependent: :destroy
  has_many :issues, dependent: :destroy

  validates :name, presence: true, uniqueness: { case_sensitive: false }
  validates :key, presence: true, uniqueness: true,
                  format: { with: /\A[A-Z]{2,10}\z/, message: "must be 2-10 uppercase letters" }

  def active_sprint
    sprints.active.first
  end
end
