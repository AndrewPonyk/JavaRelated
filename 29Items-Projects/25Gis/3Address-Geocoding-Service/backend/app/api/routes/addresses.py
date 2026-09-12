from fastapi import APIRouter, Depends, HTTPException, Path, Query, status

from app.api.deps import get_address_book_service
from app.schemas.geocode import AddressCreateRequest, AddressListResponse, AddressRecord
from app.services.address_book import AddressBookService

router = APIRouter()


@router.post("/addresses", response_model=AddressRecord, status_code=status.HTTP_201_CREATED)
async def create_address(
    payload: AddressCreateRequest,
    service: AddressBookService = Depends(get_address_book_service),
) -> AddressRecord:
    return await service.create(payload)


@router.get("/addresses", response_model=AddressListResponse)
async def list_addresses(
    limit: int = Query(default=25, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10_000),
    service: AddressBookService = Depends(get_address_book_service),
) -> AddressListResponse:
    items, total = await service.list(limit=limit, offset=offset)
    return AddressListResponse(items=items, total=total, limit=limit, offset=offset)


@router.get("/addresses/{address_id}", response_model=AddressRecord)
async def get_address(
    address_id: int = Path(ge=1),
    service: AddressBookService = Depends(get_address_book_service),
) -> AddressRecord:
    record = await service.get(address_id)
    if record is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Address not found")
    return record


@router.put("/addresses/{address_id}", response_model=AddressRecord)
async def update_address(
    payload: AddressCreateRequest,
    address_id: int = Path(ge=1),
    service: AddressBookService = Depends(get_address_book_service),
) -> AddressRecord:
    record = await service.update(address_id, payload)
    if record is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Address not found")
    return record


@router.delete("/addresses/{address_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_address(
    address_id: int = Path(ge=1),
    service: AddressBookService = Depends(get_address_book_service),
) -> None:
    deleted = await service.delete(address_id)
    if not deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Address not found")
