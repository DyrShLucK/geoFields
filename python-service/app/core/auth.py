import ee
from .config import GEE_SERVICE_ACCOUNT, GEE_JSON_KEY, GEE_PROJECT_ID


def init_gee():
    try:
        credentials = ee.ServiceAccountCredentials(GEE_SERVICE_ACCOUNT, GEE_JSON_KEY)
        ee.Initialize(credentials, project=GEE_PROJECT_ID)
        print(f" GEE Auth Success: {GEE_PROJECT_ID}")
    except Exception as e:
        print(f" GEE Auth Failed: {e}")
        raise e