export interface AdminAccount {
  id: string
  email: string
  enabled: boolean
  createdAt: string
}

export interface CreateAdminAccountRequest {
  email: string
  password: string
}
