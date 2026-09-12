from uuid import UUID

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import require_role
from app.db.session import get_db
from app.schemas.auth import RoleCreate, RoleRead, RoleUpdate, UserCreate, UserRead, UserUpdate
from app.services.user_service import UserService

router = APIRouter(dependencies=[Depends(require_role("admin"))])


@router.post("/roles", response_model=RoleRead, status_code=status.HTTP_201_CREATED)
def create_role(payload: RoleCreate, db: Session = Depends(get_db)) -> RoleRead:
    return UserService(db).create_role(payload)


@router.get("/roles", response_model=list[RoleRead])
def list_roles(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> list[RoleRead]:
    return UserService(db).list_roles(limit=limit, offset=offset)


@router.patch("/roles/{role_id}", response_model=RoleRead)
def update_role(role_id: UUID, payload: RoleUpdate, db: Session = Depends(get_db)) -> RoleRead:
    role = UserService(db).update_role(role_id, payload)
    if role is None:
        raise AppError("role_not_found", "Role not found", status.HTTP_404_NOT_FOUND)
    return role


@router.delete("/roles/{role_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_role(role_id: UUID, db: Session = Depends(get_db)) -> None:
    deleted = UserService(db).delete_role(role_id)
    if not deleted:
        raise AppError("role_not_found", "Role not found", status.HTTP_404_NOT_FOUND)


@router.post("", response_model=UserRead, status_code=status.HTTP_201_CREATED)
def create_user(payload: UserCreate, db: Session = Depends(get_db)) -> UserRead:
    return UserService(db).create_user(payload)


@router.get("", response_model=list[UserRead])
def list_users(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> list[UserRead]:
    return UserService(db).list_users(limit=limit, offset=offset)


@router.get("/{user_id}", response_model=UserRead)
def get_user(user_id: UUID, db: Session = Depends(get_db)) -> UserRead:
    user = UserService(db).get_user(user_id)
    if user is None:
        raise AppError("user_not_found", "User not found", status.HTTP_404_NOT_FOUND)
    return user


@router.patch("/{user_id}", response_model=UserRead)
def update_user(user_id: UUID, payload: UserUpdate, db: Session = Depends(get_db)) -> UserRead:
    user = UserService(db).update_user(user_id, payload)
    if user is None:
        raise AppError("user_not_found", "User not found", status.HTTP_404_NOT_FOUND)
    return user


@router.delete("/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_user(user_id: UUID, db: Session = Depends(get_db)) -> None:
    deleted = UserService(db).delete_user(user_id)
    if not deleted:
        raise AppError("user_not_found", "User not found", status.HTTP_404_NOT_FOUND)
