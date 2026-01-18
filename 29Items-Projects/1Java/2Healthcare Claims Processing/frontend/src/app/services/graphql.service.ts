import { Injectable, inject } from '@angular/core';
import { Apollo, gql } from 'apollo-angular';
import { Observable, map } from 'rxjs';
import { Claim, ClaimInput, ClaimStatus, ClaimConnection, ClaimFilter, Pagination } from '../models/claim.model';

/**
 * Service for GraphQL operations.
 */
@Injectable({
  providedIn: 'root'
})
export class GraphQLService {
  private readonly apollo = inject(Apollo);

  // Queries
  private readonly GET_CLAIM = gql`
    query GetClaim($id: UUID!) {
      claim(id: $id) {
        id
        claimNumber
        type
        status
        amount
        allowedAmount
        serviceDate
        patientId
        providerId
        fraudScore
        fraudReasons
        denialReason
        notes
        createdAt
        updatedAt
      }
    }
  `;

  private readonly GET_CLAIMS = gql`
    query GetClaims($filter: ClaimFilter, $pagination: Pagination) {
      claims(filter: $filter, pagination: $pagination) {
        edges {
          id
          claimNumber
          type
          status
          amount
          serviceDate
          patientId
          providerId
          fraudScore
          createdAt
        }
        pageInfo {
          currentPage
          pageSize
          hasNextPage
        }
      }
    }
  `;

  private readonly GET_CLAIMS_REQUIRING_REVIEW = gql`
    query GetClaimsRequiringReview {
      claimsRequiringReview {
        id
        claimNumber
        type
        status
        amount
        fraudScore
        fraudReasons
        createdAt
      }
    }
  `;

  private readonly SEARCH_CLAIMS = gql`
    query SearchClaims($query: String!, $limit: Int) {
      searchClaims(query: $query, limit: $limit) {
        id
        claimNumber
        type
        status
        amount
        serviceDate
      }
    }
  `;

  // Mutations
  private readonly SUBMIT_CLAIM = gql`
    mutation SubmitClaim($input: ClaimInput!) {
      submitClaim(input: $input) {
        id
        claimNumber
        status
      }
    }
  `;

  private readonly APPROVE_CLAIM = gql`
    mutation ApproveClaim($id: UUID!, $reviewedBy: String!, $notes: String) {
      approveClaim(id: $id, reviewedBy: $reviewedBy, notes: $notes) {
        id
        claimNumber
        status
        reviewedBy
        reviewedAt
      }
    }
  `;

  private readonly DENY_CLAIM = gql`
    mutation DenyClaim($id: UUID!, $reason: String!, $reviewedBy: String!) {
      denyClaim(id: $id, reason: $reason, reviewedBy: $reviewedBy) {
        id
        claimNumber
        status
        denialReason
      }
    }
  `;

  /**
   * Gets a claim by ID.
   */
  getClaim(id: string): Observable<Claim> {
    return this.apollo
      .watchQuery<{ claim: Claim }>({
        query: this.GET_CLAIM,
        variables: { id }
      })
      .valueChanges.pipe(map(result => result.data.claim));
  }

  /**
   * Gets paginated claims.
   */
  getClaims(filter?: ClaimFilter, pagination?: Pagination): Observable<ClaimConnection> {
    return this.apollo
      .watchQuery<{ claims: ClaimConnection }>({
        query: this.GET_CLAIMS,
        variables: { filter, pagination }
      })
      .valueChanges.pipe(map(result => result.data.claims));
  }

  /**
   * Gets claims requiring review.
   */
  getClaimsRequiringReview(): Observable<Claim[]> {
    return this.apollo
      .watchQuery<{ claimsRequiringReview: Claim[] }>({
        query: this.GET_CLAIMS_REQUIRING_REVIEW
      })
      .valueChanges.pipe(map(result => result.data.claimsRequiringReview));
  }

  /**
   * Searches claims.
   */
  searchClaims(query: string, limit: number = 20): Observable<Claim[]> {
    return this.apollo
      .watchQuery<{ searchClaims: Claim[] }>({
        query: this.SEARCH_CLAIMS,
        variables: { query, limit }
      })
      .valueChanges.pipe(map(result => result.data.searchClaims));
  }

  /**
   * Submits a new claim.
   */
  submitClaim(input: ClaimInput): Observable<Claim> {
    return this.apollo
      .mutate<{ submitClaim: Claim }>({
        mutation: this.SUBMIT_CLAIM,
        variables: { input }
      })
      .pipe(map(result => result.data!.submitClaim));
  }

  /**
   * Approves a claim.
   */
  approveClaim(id: string, reviewedBy: string, notes?: string): Observable<Claim> {
    return this.apollo
      .mutate<{ approveClaim: Claim }>({
        mutation: this.APPROVE_CLAIM,
        variables: { id, reviewedBy, notes }
      })
      .pipe(map(result => result.data!.approveClaim));
  }

  /**
   * Denies a claim.
   */
  denyClaim(id: string, reason: string, reviewedBy: string): Observable<Claim> {
    return this.apollo
      .mutate<{ denyClaim: Claim }>({
        mutation: this.DENY_CLAIM,
        variables: { id, reason, reviewedBy }
      })
      .pipe(map(result => result.data!.denyClaim));
  }
}
