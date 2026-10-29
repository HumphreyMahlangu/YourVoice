import ApiError from '../api/ApiError'

// The server's messages also serve API clients and may contain field names or
// internal states. Keep that detail in ApiError for program logic, but present
// instructions that make sense to someone managing an election in the app.
const ADMIN_MESSAGES: Record<string, string> = {
  'A verified fictional location requires verified fictional identity': 'Verify the fictional identity before marking its location verified.',
  'A matching fictional source record is required for approval': 'Configure a matching fictional source record for this voter before approving the request.',
  'The latest fictional source must verify this voter and the exact requested area': 'Check the latest source reference, fictional name and requested area. They must match exactly before approval.',
  'Approval requires an effective date at or after the current time': 'Choose an effective date and time in the future, then try again.',
  'Fictional source reference already exists': 'That fictional reference is already used. Choose a new reference for a new source decision.',
  'The fictional verification source is unavailable. Please try again later.': 'The fictional verification source is unavailable. Please try again later.',
  'Configure this province before using it as a contest scope': 'Add this province in geographic administration before creating the contest.',
  'The contest municipality must belong to its configured province': 'Choose a municipality configured under the contest province.',
  'Configure this ward within the selected province and municipality before using it as a contest scope': 'Configure the ward under the matching province and municipality before creating the contest.',

  'Request validation failed': 'Check the highlighted fields and try again.',
  'Request conflicts with existing persisted data':
    'This information is already in use. Check your entries and try again.',
  'Election lifecycle transitions are automatic; administrators may only cancel an active election':
    'Election stages change automatically. You can cancel an election that is still active.',
  'Contest lifecycle transitions are automatic and cannot be performed by an administrator':
    'Contest stages change automatically. You do not need to change them yourself.',
  'Only DRAFT elections can be edited; cancel an election that has already opened':
    'This election has already opened, so its details cannot be changed. If it must stop, you can cancel it.',
  'Contest ballot options can be changed only while the contest is DRAFT':
    'This contest has already opened, so its ballot choices cannot be changed.',
  'Ballot configuration is locked once registration opens':
    'Registration has opened, so the ballot cannot be changed.',
  'Ballot configuration is closed because the registration window has ended; cancel this election and create a new one':
    'Registration has ended. Cancel this election and create a new one if the ballot needs changes.',
  'The registration window must end in the future':
    'Choose a registration closing time in the future.',
  'Registration window must start before it ends':
    'Registration must open before it closes. Check the dates and times.',
  'Voting window must start before it ends':
    'Voting must open before it closes. Check the dates and times.',
  'Registration must end before voting starts':
    'Registration must close before voting opens. Check the dates and times.',
  'A user account with this email already exists':
    'An account already uses this email address. Enter a different one.',
  'An election with this name already exists':
    'An election already has this name. Choose a different name.',
  'A voting district with this code already exists':
    'A voting district already uses this code. Choose a different code.',
  'A contest with this name already exists for this election':
    'This election already has a contest with that name. Choose a different name.',
  'A contest with this display order already exists for this election':
    'Another contest has this position. Choose a different position.',
  'A ballot option with this name already exists for this contest':
    'This contest already has a ballot choice with that name. Choose a different name.',
  'A ballot option with this display order already exists for this contest':
    'Another ballot choice has this position. Choose a different position.',
  'This voting district has registered voters; only its code and name can be changed':
    'Voters are already registered in this district. You can change its code or name, but not its area.',
  'This voting district has registered voters and cannot be deleted':
    'Voters are already registered in this district, so it cannot be deleted.',
  'National contests must not declare a geographic scope':
    'A national contest covers the whole country. Leave the province, municipality and ward empty.',
  'Provincial contests require scopeProvince only':
    'Choose a province. Leave the municipality and ward empty.',
  'Municipal PR contests require scopeProvince and scopeMunicipality only':
    'Choose a province and municipality. Leave the ward empty.',
  'Municipal ward contests require scopeProvince, scopeMunicipality, and scopeWardNumber':
    'Choose a province, municipality and ward number.',
}

const FIELD_LABELS: Record<string, string> = {
  code: 'District code',
  displayOrder: 'Position',
  email: 'Email address',
  municipality: 'Municipality',
  name: 'Name',
  optionType: 'Ballot choice type',
  password: 'Password',
  province: 'Province',
  registrationEndAt: 'Registration closing time',
  registrationStartAt: 'Registration opening time',
  scopeMunicipality: 'Municipality',
  scopeProvince: 'Province',
  scopeWardNumber: 'Ward number',
  type: 'Type',
  votingEndAt: 'Voting closing time',
  votingStartAt: 'Voting opening time',
  wardNumber: 'Ward number',
}

export function getAdminFieldErrors(error: ApiError): Readonly<Record<string, string>> {
  return Object.fromEntries(
    Object.entries(error.fieldErrors).map(([field, message]) => {
      const label = FIELD_LABELS[field] ?? 'This field'
      if (/must not be blank|must not be null/.test(message)) {
        return [field, `${label} is required.`]
      }
      if (message === 'must be a well-formed email address') {
        return [field, 'Enter a valid email address.']
      }
      const size = message.match(/^size must be between (\d+) and (\d+)$/)
      if (size) {
        return [field, `${label} must be between ${size[1]} and ${size[2]} characters.`]
      }
      const minimum = message.match(/^must be greater than or equal to (\d+)$/)
      if (minimum) {
        return [field, `${label} must be ${minimum[1]} or more.`]
      }
      const maximum = message.match(/^must be less than or equal to (\d+)$/)
      if (maximum) {
        return [field, `${label} must be ${maximum[1]} or less.`]
      }
      if (message === 'must contain only letters, digits, and hyphens') {
        return [field, 'Use only letters, numbers and hyphens.']
      }
      if (message === 'must contain at least one uppercase letter, one lowercase letter, and one digit') {
        return [field, 'Use at least one capital letter, one small letter and one number.']
      }
      return [field, `Check ${label.toLowerCase()} and try again.`]
    }),
  )
}

export function getAdminErrorMessage(error: unknown, action: string): string {
  if (error instanceof ApiError) {
    const known = ADMIN_MESSAGES[error.message]
    if (known) return known

    if (/^A (BLANK_BALLOT|SPOILT_BALLOT) option already exists/.test(error.message)) {
      return 'This contest already has that type of ballot choice.'
    }
    if (error.message.startsWith('Contest type ') && error.message.includes(' is not valid for a ')) {
      return 'This contest type does not match the election type. Choose a different contest type.'
    }
    if (error.status === 400 || error.status === 422) {
      return 'Check the information you entered and try again.'
    }
    if (error.status === 401) return 'Your session has ended. Sign in again to continue.'
    if (error.status === 403) return 'You do not have permission to do this.'
    if (error.status === 404) return 'This item could not be found. It may have been removed.'
    if (error.status === 409) return 'This change cannot be made now. Refresh the page and check its current details.'
    if (error.status === 429) return 'Too many attempts. Wait a moment and try again.'
    return `${action} Please try again. If the problem continues, ask for help.`
  }

  if (error instanceof TypeError) {
    return 'Could not connect. Check your internet connection and try again.'
  }
  // Only show messages written for local form checks. Other errors can contain
  // browser or library details that do not help the administrator recover.
  if (error instanceof Error && [
    'Registration must start before it ends.',
    'Registration must end before voting starts.',
    'Voting must start before it ends.',
    'This election could not be found. Return to the admin dashboard and choose an election.',
  ].includes(error.message)) return error.message
  return `${action} Please try again.`
}
