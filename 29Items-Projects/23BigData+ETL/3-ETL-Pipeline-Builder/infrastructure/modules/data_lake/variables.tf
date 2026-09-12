variable "name" {
  description = "Global-unique bucket prefix (e.g. etl-pipeline-builder-dev)"
  type        = string
}

variable "env" {
  type = string
}

variable "glue_workers" {
  type    = number
  default = 2
}

variable "tags" {
  type    = map(string)
  default = {}
}
