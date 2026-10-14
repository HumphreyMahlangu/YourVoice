import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import ApiError from '../api/ApiError'
import {
  deleteVotingDistrict,
  updateVotingDistrict,
} from '../api/adminManagement'
import { getVotingDistricts } from '../api/districts'
import { useAuth } from '../auth/useAuth'
import type { VotingDistrict } from '../types/district'
import { getErrorMessage } from '../utils/getErrorMessage'

interface DistrictEditFormProps {
  district: VotingDistrict
  authToken: string
  onSaved: (district: VotingDistrict) => void
  onCancel: () => void
  onUnauthorized: () => void
}

function DistrictEditForm({
  district,
  authToken,
  onSaved,
  onCancel,
  onUnauthorized,
}: DistrictEditFormProps) {
  const [code, setCode] = useState(district.code)
  const [name, setName] = useState(district.name)
  const [province, setProvince] = useState(district.province)
  const [municipality, setMunicipality] = useState(district.municipality)
  const [wardNumber, setWardNumber] = useState(String(district.wardNumber))
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const idPrefix = `district-${district.id}`

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSaving(true)

    try {
      onSaved(
        await updateVotingDistrict(
          district.id,
          {
            code,
            name,
            province,
            municipality,
            wardNumber: Number(wardNumber),
          },
          authToken,
        ),
      )
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onUnauthorized()
        return
      }
      setError(requestError)
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label={`Edit ${district.name}`}>
      <p>
        Once voters are registered in this district, only the code and name can
        change: the province, municipality, and ward decide which contests they
        can vote in.
      </p>
      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to update the voting district.')}
        </p>
      )}
      <div>
        <label htmlFor={`${idPrefix}-code`}>District code</label>
        <input
          id={`${idPrefix}-code`}
          required
          maxLength={32}
          pattern="[A-Za-z0-9-]+"
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor={`${idPrefix}-name`}>District name</label>
        <input
          id={`${idPrefix}-name`}
          required
          maxLength={160}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor={`${idPrefix}-province`}>Province</label>
        <input
          id={`${idPrefix}-province`}
          required
          maxLength={80}
          value={province}
          onChange={(event) => setProvince(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor={`${idPrefix}-municipality`}>Municipality</label>
        <input
          id={`${idPrefix}-municipality`}
          required
          maxLength={160}
          value={municipality}
          onChange={(event) => setMunicipality(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor={`${idPrefix}-ward`}>Ward number</label>
        <input
          id={`${idPrefix}-ward`}
          type="number"
          required
          min={1}
          max={9999}
          value={wardNumber}
          onChange={(event) => setWardNumber(event.target.value)}
        />
      </div>
      <button type="submit" disabled={isSaving}>
        {isSaving ? 'Saving...' : 'Save district'}
      </button>{' '}
      <button type="button" disabled={isSaving} onClick={onCancel}>
        Cancel
      </button>
    </form>
  )
}

function AdminVotingDistrictsPage() {
  const { session, logout } = useAuth()
  const [districts, setDistricts] = useState<VotingDistrict[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [actionError, setActionError] = useState<unknown>(null)

  useEffect(() => {
    const abortController = new AbortController()

    async function loadDistricts() {
      setLoadError(null)
      setIsLoading(true)

      try {
        const response = await getVotingDistricts(abortController.signal)
        if (!abortController.signal.aborted) {
          setDistricts(response)
        }
      } catch (requestError) {
        if (!abortController.signal.aborted) {
          setLoadError(requestError)
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadDistricts()
    return () => abortController.abort()
  }, [])

  async function handleDelete(districtId: string) {
    if (!session) {
      return
    }
    setActionError(null)
    setIsDeleting(true)

    try {
      await deleteVotingDistrict(districtId, session.accessToken)
      setDistricts((current) =>
        current.filter((district) => district.id !== districtId),
      )
      setPendingDeleteId(null)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }
      setActionError(requestError)
    } finally {
      setIsDeleting(false)
    }
  }

  function handleSaved(updated: VotingDistrict) {
    setDistricts((current) =>
      current.map((district) =>
        district.id === updated.id ? updated : district,
      ),
    )
    setEditingId(null)
  }

  return (
    <section className="page admin-page workflow-page" aria-labelledby="voting-districts-heading">
      <Link to="/admin">Back to admin dashboard</Link>
      <h1 id="voting-districts-heading">Voting districts</h1>
      <p>
        <Link to="/admin/voting-districts/new">Create a voting district</Link>
      </p>

      {isLoading && <p role="status">Loading voting districts...</p>}

      {!isLoading && loadError !== null && (
        <p role="alert">
          {getErrorMessage(loadError, 'Unable to load voting districts.')}
        </p>
      )}

      {actionError !== null && (
        <p role="alert">
          {getErrorMessage(actionError, 'Unable to delete the voting district.')}
        </p>
      )}

      {!isLoading && loadError === null && districts.length === 0 && (
        <p>No voting districts have been created.</p>
      )}

      {!isLoading && loadError === null && districts.length > 0 && (
        <ul>
          {districts.map((district) => (
            <li key={district.id}>
              <article aria-labelledby={`district-heading-${district.id}`}>
                <h2 id={`district-heading-${district.id}`}>
                  {district.code}: {district.name}
                </h2>
                <p>
                  {district.municipality}, {district.province}, ward{' '}
                  {district.wardNumber}
                </p>

                {editingId === district.id && session ? (
                  <DistrictEditForm
                    district={district}
                    authToken={session.accessToken}
                    onSaved={handleSaved}
                    onCancel={() => setEditingId(null)}
                    onUnauthorized={logout}
                  />
                ) : pendingDeleteId === district.id ? (
                  <p>
                    Delete {district.code}? This cannot be undone.{' '}
                    <button
                      type="button"
                      disabled={isDeleting}
                      onClick={() => void handleDelete(district.id)}
                    >
                      {isDeleting ? 'Deleting...' : 'Confirm delete'}
                    </button>{' '}
                    <button
                      type="button"
                      disabled={isDeleting}
                      onClick={() => setPendingDeleteId(null)}
                    >
                      Keep district
                    </button>
                  </p>
                ) : (
                  <p>
                    <button type="button" onClick={() => setEditingId(district.id)}>
                      Edit
                    </button>{' '}
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null)
                        setPendingDeleteId(district.id)
                      }}
                    >
                      Delete
                    </button>
                  </p>
                )}
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default AdminVotingDistrictsPage
